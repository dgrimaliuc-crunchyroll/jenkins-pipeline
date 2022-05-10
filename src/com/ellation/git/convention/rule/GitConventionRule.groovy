package com.ellation.git.convention.rule

import com.ellation.git.GitInfo

/**
 * Base class which describes git convention rules contract.
 */
abstract class GitConventionRule {
    /**
     * Verifies that rule's requirements are fulfilled.
     * @param gitInfo various information related to Git like commit message, branch name
     */
    abstract boolean verify(GitInfo gitInfo)

    /**
     * User-friendly rule description.
     */
    abstract String description()
}
