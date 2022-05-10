package com.ellation.ef

@SuppressWarnings('PropertyName')
/**
 * 2019-04-18
 * The branch was developed before this codenarc rule.
 * To unblock dependant work, this will be refactored
 * after the merge in TC-125
 */
class ServiceRevision implements Serializable {
    /**
     *  @SuppressWarnings('PropertyName')
     */
    String value
    String build_number
    String pipeline_build_number
    String commit_hash
    String last_modified
    String modified_by
    String version_id
    String location
    String status
    String name
}
