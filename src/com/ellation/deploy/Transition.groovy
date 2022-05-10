package com.ellation.deploy

/**
 * Each state is a JIRA Transition,corresponding the the workflow used in DPLY.
 * A transition is identified by a numeric id. We use the keys to make the code
 * more readable when we do transitions and to have  control over configuration change in a
 * single place.
 */
enum Transition {
    SELECTED_FOR_DEPLOY('181'),
    BUILD('161'),
    STAGING_DEPLOY('21'),
    AUTOMATED_TESTS('31'),
    FAILED('151'),
    MANUAL_QA('51'),
    STAGING_TO_MANUAL_QA('171'),
    PROMOTE('61'), IN_PROD('71'),
    SMOKE_TESTS('81'),
    DEPLOYED('101'),
    PROMOTE_TO_PROD('71'),
    FAILED_TO_CLOSED('191') ,
    ANY_TO_FAILED('221'),
    CLOSED('281')

    Transition(String value) {
        this.value = value
    }

    private final String value

    String getValue() {
        return value
    }

    String toString() {
        return name() + " = " + value
    }

}
