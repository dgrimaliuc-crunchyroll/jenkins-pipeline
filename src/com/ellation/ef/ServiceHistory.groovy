package com.ellation.ef

class ServiceHistory implements  Serializable {

    String rawHistory = ''

    ArrayList entries = []

    String serviceName

    ServiceHistory(input) {
        rawHistory = input

        ArrayList lines = rawHistory.split("\n")

        String env = lines[0].split("-")[0]
        serviceName = lines[0].replace("${env}-", "").split(" ")[0]

        // Remove the header line that describes what each column is
        lines.remove(0)

        lines.each {
            def parts = it.split(" ")

            if (parts.size() != 9) {
                throw new IllegalArgumentException("Invalid history entry size")
            }

            ServiceRevision entry = new ServiceRevision(
                value: parts[0],
                build_number:parts[1],
                pipeline_build_number:parts[2],
                commit_hash:parts[3],
                last_modified:parts[4],
                modified_by: parts[5],
                version_id: parts[6],
                location: parts[7],
                status: parts[8],
                name: serviceName
            )

            entries += entry
        }
    }

    /**
     * Utility method which returns last stable ami
     * @return ServiceRevision, can be null if no match found
     */
    def latestStable() {
        return this.entries.find {
            it['status'] == 'stable'
        }
    }

    /**
     * Finds latest  entry with the given ami id
     * @NonCPS
     * @param amiId
     * @return ServiceRevision, can be null if no match found
     */
    def find(amiId) {
        return this.entries.find {
            it['value'] == amiId
        }
    }

    /**
     * Returns the latest entry in this service's history.
     * @return latest entry of this service's ServiceRevision, or null if it has no history
     */
    ServiceRevision latest() {
        if (entries.isEmpty()) {
            return null
        }
        return this.entries.first()
    }

    /**
     * Returns the first entry of this service's history
     * @return first entry of this service's ServiceRevision, or null if it has no history
     */
    ServiceRevision first() {
        if (entries.isEmpty()) {
            return null
        }
        return this.entries.last()
    }

    @Override
    String toString() {
        return "History{" +
                "rawHistory=" + rawHistory +
                '}'
    }

}
