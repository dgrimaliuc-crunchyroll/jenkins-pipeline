def call() {
    def upstream = currentBuild.rawBuild.getCause(hudson.model.Cause$UpstreamCause)

    return upstream?.shortDescription ?: ""
}
