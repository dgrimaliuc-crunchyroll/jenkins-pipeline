# Groovy Style Guide

This guide extends and overrides http://groovy-lang.org/style-guide.html.
Code style verification is accomplished via [CodeNarc](http://codenarc.sourceforge.net) plugin.
Check [rules.groovy](./config/codenarc/rules.groovy) to see which rules are enabled/disabled.

Some rules are not enabled because they are breaking Jenkins pipeline code, even though those rules
make sense for pure Groovy code.

### General rules

- Indentation (4 spaces)
- No semicolon
- Spaces before/after keywords
- Spaces around operators/braces
- No spaces whithin parentheses/control statements
- Max line limit is 100
- Opening brackets on the same line
- Favor explicit return over implicit (improves readability for non-Groovy contributors/reviewers)
- Favor explicit return types over implicit if possible (String vs def)
- Divide tests into 3 sections ("give/when/then" or Arrange/Act/Assert) separated by new lines

### Blank lines usage

- Maximum blank lines before package statement: 0
- Maximum blank lines: 1
- Blank lines after package statement: 1
- Blank lines around class: 1
- Blank lines around method: 1
- Blank line at end of file: 1


#### Example

```groovy
package com.ellation.example

import com.ellation.AnotherClass

/**
 * Sample class
 */
class SampleClass implements Serializable {

    private final String parameter

    SampleClass(String parameter) {
        this.parameter = parameter
    }

    int getApplicationId(String applicationName) {
        def response = requestAppIds(applicationName)
        for (app in response.applications) {
            if (app.name == applicationName) {
                return app.id
            }
        }
        return 0
    }
}

```

```groovy
#!groovy

@Library('ellation') _

node('universal') {
    timestamps {
        stage('checkout') {
            checkout scm
        }

        stage('code quality') {
            sh "${env.WORKSPACE}/gradlew codenarcAllSources"
        }

        stage('test') {
            sh "${env.WORKSPACE}/gradlew test integrationTest"
        }
    }
}

```
