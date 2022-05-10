package com.ellation.ef

import com.ellation.ef.exception.InvalidCommitHashException

/**
 * ef-version contains commit hash information. It may be a single commit, or several commits
 * joined by a comma ','. In the later case, we also have an identifier, the repository name
 * in the form repo-name=commit-hash. An example, with 2 entries will be:
 * test-instance=04636b,computer-science=a51e9b. The real entry will have the full commit hash,
 * for API DOC i used only 6 characters to keep it brief.
 */
class CommitHashInformation implements Serializable {

    /**
     *  Stores the value of a commit hash as stored in ef-registry
     */
    private String commitHash

    CommitHashInformation(String commitHash) {
        this.commitHash = commitHash
    }

    /**
     * Returns the commit hash
     * If single commit hash with key is detected, the key is removed
     * for backwards compatibility reasons
     * @return
     */
    String getCommitHash() {
        if (isSingleEntryWithKey()) {
            String[] parts  = commitHash.split('=')
            return parts[1]
        }

        return commitHash
    }

    boolean isMultiple() {
        return commitHash.contains(',')
    }

    /**
     * The new pipelines record the commit as repoKey=sha even if it is a single repo
     * This method tells us if this is the case.
     * For now we have a combination of nonKeyed sha and keyed sha so we need to make
     * a distinction.
     * @return
     */
    boolean isSingleEntryWithKey() {
        return !isMultiple() && commitHash.contains('=')
    }

    /**
     * If this is a multi hash, it will split,  parse it and construct a
     * HashMap with repo key and commit information.
     * Initial implementation had this done in the constructor, but Jenkins
     * has issue with this code in constructor and spits out a error similar to
     * https://stackoverflow.com/questions/51340854/jenkins-shared-library-error-com-cloudbees-groovy-cps-impl-cpscallableinvocation
     * @return HashMap<String,String> the key is the repo key , the value is the hash
     * @throws InvalidCommitHashException
     */
    HashMap<String, String> getCommits() {
        HashMap<String,String> commits = [:]
        if (isMultiple()) {
            commitHash.split(',').each {
                def parts = it.split('=')
                if (parts.size() != 2) {
                    throw new InvalidCommitHashException("Invalid commit hash entry ${it}")
                }
                commits[parts[0]] = parts[1]
            }
        }
        return commits
    }
}
