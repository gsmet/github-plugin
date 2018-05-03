package com.cloudbees.jenkins;

import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import javax.annotation.CheckForNull;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hudson.plugins.git.BranchSpec;

/**
 * Uniquely identifies a branch for a given repository on GitHub.
 *
 * @author Guillaume Smet
 * @since 1.30
 */
public class GitHubRepositoryBranchSpec {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitHubRepositoryBranchSpec.class);

    @CheckForNull
    public static GitHubRepositoryBranchSpec create(GitHubRepositoryName repositoryName, BranchSpec branchSpec) {
        LOGGER.debug("Constructing from repository {} and branchSpec {}", repositoryName, branchSpec);

        return new GitHubRepositoryBranchSpec(repositoryName, branchSpec);
    }

    @SuppressWarnings("visibilitymodifier")
    public final GitHubRepositoryName repositoryName;
    @SuppressWarnings("visibilitymodifier")
    public final BranchSpec branchSpec;

    public GitHubRepositoryBranchSpec(GitHubRepositoryName repositoryName, BranchSpec branchSpec) {
        this.repositoryName = repositoryName;
        this.branchSpec = branchSpec;
    }

    public GitHubRepositoryName getRepositoryName() {
        return repositoryName;
    }

    public BranchSpec getBranchSpec() {
        return branchSpec;
    }

    @CheckForNull
    public boolean matches(GitHubRepositoryName repositoryName, String ref) {
        if (!this.repositoryName.equals(repositoryName)) {
            return false;
        }

        return branchSpec.matches(ref);
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(repositoryName).append(branchSpec).build();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                .append("repositoryName", repositoryName).append("branchSpec", branchSpec).build();
    }
}
