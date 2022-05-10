import jenkins.model.Jenkins

void updateCloudAMIFromUpstream(Map jobToCloudMapping) {
    List<String> upstreamLogs = getUpstreamLogs(100)
    String amiID = findAmiIDInLog(upstreamLogs)
    if (amiID == null) {
        currentBuild.result = 'FAILURE'
        println("Could not find the amiID in the upstream job")
        return
    }
    println("Found amiID: ${amiID} in upstream job")

    def upstreamJobName = getUpstreamJobName()
    def cloudMapping = jobToCloudMapping[upstreamJobName]
    if  (!(cloudMapping instanceof Collection)) {
        cloudMapping=[cloudMapping]
    }

    for (def cloudAMI in cloudMapping) {
        def cloudName = cloudAMI.cloudName
        def templateDescription = cloudAMI.templateDescription
        println("Updating template with description <${templateDescription}> in cloud <${cloudName}> with amiID: ${amiID}")
        updateCloudSlaveAMI(cloudName, templateDescription, amiID)
    }
}

void updateCloudSlaveAMI(String cloudName, String templateDescription, String amiID) {
    def cloud = getCloudByName(cloudName)
    def template = cloud.getTemplate(templateDescription)

    template.setAmi(amiID)
}

String findAmiIDInLog(List<String> logLines) {
    for (line in logLines.reverse()) {
        def match = line =~ /(ami-[A-Fa-f0-9]{8,})/
        if (match) {
            amiId = match[0][1]
            return amiId
        }
    }
    return null
}

def getRunCause() {
    def build = currentBuild.rawBuild
    // the first element in the causes list is the build that directly triggered the run
    return build.getCauses()[0]
}

List<String> getUpstreamLogs(lineCount) {
    return getRunCause().getUpstreamRun().getLog(lineCount)
}

String getUpstreamJobName() {
    // misleading name; it only returns a string with the upstream job name
    return getRunCause().getUpstreamProject()
}

def getCloudByName(String cloudName) {
    Jenkins jenkins = Jenkins.getInstance()
    def cloud = jenkins.clouds.find { it.getCloudName() == cloudName }
    return cloud
}
