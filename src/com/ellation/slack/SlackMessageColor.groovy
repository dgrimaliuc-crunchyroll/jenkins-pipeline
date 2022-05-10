package com.ellation.slack

/**
 * Simple mapping of <a href="https://github.com/jenkinsci/slack-plugin">Slack Notifier Jenkins plugin</a>
 * colors values to enum constants.
 */
enum SlackMessageColor implements Serializable {
    GOOD("good"),
    DANGER("danger"),
    WARNING("warning")

    private final String color

    SlackMessageColor(String color) {
        this.color = color
    }

    @Override
    String toString() {
        color
    }
}
