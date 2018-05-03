package com.cloudbees.jenkins;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hudson.EnvVars;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.EnvironmentContributor;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import jenkins.model.Jenkins;
import jenkins.triggers.SCMTriggerItem;
import jenkins.triggers.SCMTriggerItem.SCMTriggerItems;

/**
 * Extension point that associates {@link GitHubRepositoryBranchSpec}s to a project.
 *
 * @author Kohsuke Kawaguchi
 * @author Guillaume Smet
 * @since 1.30
 */
public abstract class GitHubRepositoryBranchSpecContributor implements ExtensionPoint {
    private static final Logger LOGGER = LoggerFactory.getLogger(GitHubRepositoryBranchSpecContributor.class);

    /**
     * Looks at the definition of {@link AbstractProject} and list up the related github repositories,
     * then puts them into the collection.
     *
     * @deprecated Use {@link #parseAssociatedRefs(Item, Collection)}
     */
    @Deprecated
    public void parseAssociatedRefs(AbstractProject<?, ?> job, Collection<GitHubRepositoryBranchSpec> result) {
        parseAssociatedRefs((Item) job, result);
    }

    /**
     * Looks at the definition of {@link Job} and list up the related github repositories,
     * then puts them into the collection.
     * @deprecated Use {@link #parseAssociatedRefs(Item, Collection)}
     */
    @Deprecated
    public /*abstract*/ void parseAssociatedRefs(Job<?, ?> job, Collection<GitHubRepositoryBranchSpec> result) {
        parseAssociatedRefs((Item) job, result);
    }

    /**
     * Looks at the definition of {@link Item} and list up the related github repositories,
     * then puts them into the collection.
     * @param item the item.
     * @param result the collection to add repository names to
     * @since 1.25.0
     */
    @SuppressWarnings("deprecation")
    public /*abstract*/ void parseAssociatedRefs(Item item, Collection<GitHubRepositoryBranchSpec> result) {
        if (Util.isOverridden(
                GitHubRepositoryBranchSpecContributor.class,
                getClass(),
                "parseAssociatedRefs",
                Job.class,
                Collection.class
        )) {
            // if this impl is legacy, it cannot contribute to non-jobs, so not an error
            if (item instanceof Job) {
                parseAssociatedRefs((Job<?, ?>) item, result);
            }
        } else  if (Util.isOverridden(
                GitHubRepositoryBranchSpecContributor.class,
                getClass(),
                "parseAssociatedRefs",
                AbstractProject.class,
                Collection.class
        )) {
            // if this impl is legacy, it cannot contribute to non-projects, so not an error
            if (item instanceof AbstractProject) {
                parseAssociatedRefs((AbstractProject<?, ?>) item, result);
            }
        } else {
            throw new AbstractMethodError("you must override the new overload of parseAssociatedRefs");
        }
    }

    public static ExtensionList<GitHubRepositoryBranchSpecContributor> all() {
        return Jenkins.getInstance().getExtensionList(GitHubRepositoryBranchSpecContributor.class);
    }

    /**
     * @deprecated Use {@link #parseAssociatedRefs(Job)}
     */
    @Deprecated
    public static Collection<GitHubRepositoryBranchSpec> parseAssociatedRefs(AbstractProject<?, ?> job) {
        return parseAssociatedRefs((Item) job);
    }

    /**
     * @deprecated Use {@link #parseAssociatedRefs(Item)}
     */
    @Deprecated
    public static Collection<GitHubRepositoryBranchSpec> parseAssociatedRefs(Job<?, ?> job) {
        return parseAssociatedRefs((Item) job);
    }

    public static Collection<GitHubRepositoryBranchSpec> parseAssociatedRefs(Item item) {
        Set<GitHubRepositoryBranchSpec> names = new HashSet<GitHubRepositoryBranchSpec>();
        for (GitHubRepositoryBranchSpecContributor c : all()) {
            c.parseAssociatedRefs(item, names);
        }
        return names;
    }

    /**
     * Default implementation that looks at SCMs
     */
    @Extension
    public static class FromSCM extends GitHubRepositoryBranchSpecContributor {
        @Override
        public void parseAssociatedRefs(Item item, Collection<GitHubRepositoryBranchSpec> result) {
            SCMTriggerItem triggerItem = SCMTriggerItems.asSCMTriggerItem(item);
            EnvVars envVars = item instanceof Job ? buildEnv((Job) item) : new EnvVars();
            if (triggerItem != null) {
                for (SCM scm : triggerItem.getSCMs()) {
                    addBranchSpec(scm, envVars, result);
                }
            }
        }

        protected EnvVars buildEnv(Job<?, ?> job) {
            EnvVars env = new EnvVars();
            for (EnvironmentContributor contributor : EnvironmentContributor.all()) {
                try {
                    contributor.buildEnvironmentFor(job, env, TaskListener.NULL);
                } catch (Exception e) {
                    LOGGER.debug("{} failed to build env ({}), skipping", contributor.getClass(), e.getMessage(), e);
                }
            }
            return env;
        }

        protected static void addBranchSpec(SCM scm, EnvVars env, Collection<GitHubRepositoryBranchSpec> r) {
            if (scm instanceof GitSCM) {
                GitSCM git = (GitSCM) scm;
                // There is an issue with '*/master' which is the default: the
                // pattern generated by BranchSpec#getPattern() is incorrect
                // (there is one additional / in the regexp) so we need to fix
                // up the branch specs.
                List<BranchSpec> branchSpecs = new ArrayList<>();
                for (BranchSpec originalBranchSpec : git.getBranches()) {
                    branchSpecs.add(new BranchSpec(originalBranchSpec.getName().replaceAll("^\\*+/", "")));
                }
                for (RemoteConfig rc : git.getRepositories()) {
                    for (URIish uri : rc.getURIs()) {
                        String url = env.expand(uri.toString());
                        GitHubRepositoryName repo = GitHubRepositoryName.create(url);
                        if (repo != null) {
                            for (BranchSpec branchSpec : branchSpecs) {
                                r.add(GitHubRepositoryBranchSpec.create(repo, branchSpec));
                            }
                        }
                    }
                }
            }
        }
    }
}
