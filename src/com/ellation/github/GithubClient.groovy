package com.ellation.github

import com.ellation.github.exception.InvalidGithubResponse
import com.ellation.github.exception.InvalidUrlException
import groovy.json.JsonSlurperClassic

class GithubClient implements Serializable {

    RestClient restClient

    GithubClient(RestClient restClient) {
        this.restClient = restClient
    }

    Object getReleaseNotesFromUrl(String url) {
        def group = (url =~ /https:\/\/github.com\/(.*)\/(.*)\/releases\/tag\/(.*)/)

        if (group.size() == 0) {
            throw new InvalidUrlException('Invalid url')
        }

        String owner = group[0][1]
        String repo = group[0][2]
        String tag = group[0][3]

        return getReleaseByTag(owner, repo, tag)
    }

    /**
     * @param owner the github owner
     * @param repo the github repository
     * @param tag the tag name
     * @return a data structure of lists and maps
     */
    Object getReleaseByTag(String owner, String repo, String tag) {
        HttpResponse response = restClient.get("/repos/${owner}/${repo}/releases/tags/${tag}")

        if (response.status != 200) {
            throw new InvalidGithubResponse("Something went wrong. Received ${response.status} response code instead of 200 for GET /repos/${owner}/${repo}/releases/tags/${tag}\n\nResponse: ${response.content}")
        }

        return new JsonSlurperClassic().parseText(response.content)
    }

    /**
     * @param owner the github owner
     * @param repo the github repository
     * @param base base commit/tag
     * @param head head commit/tag
     * @return a data structure of lists and maps
     */
    Object getCommitsDiff(String owner, String repo, String base, String head) {
        HttpResponse response = restClient.get("/repos/${owner}/${repo}/compare/${base}...${head}")

        if (response.status != 200) {
            throw new InvalidGithubResponse("Something went wrong. Received ${response.status} response code instead of 200 for GET /repos/${owner}/${repo}/compare/${base}...${head}\n\nResponse: ${response.content}")
        }

        return new JsonSlurperClassic().parseText(response.content)
    }

    /**
     * @param owner the github owner
     * @param repo the github repository
     * @param number the pull request number
     * @return a data structure of lists and maps
     */
    Object getIssue(String owner, String repo, String number) {
        HttpResponse response = restClient.get("/repos/${owner}/${repo}/issues/${number}")

        if (response.status != 200) {
            throw new InvalidGithubResponse("Something went wrong. Received ${response.status} response code instead of 200 for GET /repos/${owner}/${repo}/issues/${number}\n\nResponse: ${response.content}")
        }

        return new JsonSlurperClassic().parseText(response.content)
    }

}
