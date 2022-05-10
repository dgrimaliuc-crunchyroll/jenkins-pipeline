package com.ellation.git

/**
 * These are the git formats we use in GitHelper.
 * See the documentation for the formats themselves
 * https://git-scm.com/docs/pretty-formats
 */
enum GitFormat {
    BODIES('%B'),
    HASHES_AND_BODIES('%H%n%B'),

    private final String value

    GitFormat(String value) {
        this.value = value
    }
}

