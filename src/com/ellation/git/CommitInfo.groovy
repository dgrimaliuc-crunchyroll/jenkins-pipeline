package com.ellation.git

import com.cloudbees.groovy.cps.NonCPS

class CommitInfo implements Serializable {

    String commitId   = ""
    String msg        = ""
    String ticket     = "N/A"
    String preDeploy  = null
    String postDeploy = null
    List<String> libraries  = []

    /**
     * Factory method used to create CommitInfo from message body formatted as %H%n%B%
     * @link https://git-scm.com/docs/pretty-formats
     */
    static CommitInfo from(String commitMessage) {
        def commit = new CommitInfo()

        def lines = commitMessage.readLines()

        commit.commitId   = lines[0]
        commit.msg        = lines[1]
        commit.ticket     = findTicket(lines)
        commit.preDeploy  = extractFromPattern(lines, /(?i)pre-?deploy\s?:\s?(.*)/)
        commit.postDeploy = extractFromPattern(lines, /(?i)post-?deploy\s?:\s?(.*)/)
        commit.libraries  = extractAllGitHubTagUrls(lines)

        return commit
    }

    private static String findTicket(List<String>  commitLines) {
        def ticket = extractFromPattern(commitLines, /(?i)jira\s?:\s?([a-zA-Z]+\-[0-9]+)/)

        return ticket == null ? 'N/A' : ticket
    }

    private static String extractFromPattern(List<String> commitLines, String pattern) {
        def matchingLine = commitLines.find {
            (it =~ pattern)
        }

        if (!matchingLine) {
            return null
        }

        def matches = (matchingLine =~ /$pattern/)

        if (matches.count) {
            return matches[0][1]
        }
    }

    /**
     * @param commitLines
     * @return List<String> Example: ['https://github.com/crunchyroll/test-instance/releases/tag/0.1.0', 'https://github.com/crunchyroll/test-instance/releases/tag/0.2.0']
     */
    @NonCPS
    private static List<String> extractAllGitHubTagUrls(List<String> commitLines) {
        String linePattern = /(?i)librarynotes\s?:\s?(.*)/
        String repoPattern = /\b(https?):\/\/github.com\/(crunchyroll|ellationengc)\/(.+)\/releases\/tag\/[-a-zA-Z0-9+&@#%?=~_|!:,.;]+/

        List<String> matchingLines = commitLines.findAll {
            (it =~ linePattern)
        }

        List<String> out = []

        for ( int i = 0; i < matchingLines.size(); i++) {
            def  matches = (matchingLines[i] =~ /$repoPattern/)
            if (matches.size() > 0) {
                String match = matches[0][0]
                match.split(' ').each {
                    out.add((String)it)
                }
            }
        }

        return out
    }
}
