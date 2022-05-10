import hudson.FilePath
import hudson.model.FileParameterValue
import hudson.model.ParametersAction
import hudson.model.StringParameterValue

def call() {
    def workspace

    if (env['NODE_NAME'].equals("master")) {
        workspace = new FilePath(null, env['WORKSPACE'])
    } else {
        workspace = new FilePath(Jenkins.getInstance().getComputer(env['NODE_NAME']).getChannel(), env['WORKSPACE'])
    }

    def paramsAction = currentBuild.rawBuild.getAction(ParametersAction.class);
    if (paramsAction != null) {
        for (param in paramsAction.getParameters()) {
            if (param instanceof StringParameterValue && param.getName().startsWith("file:")) {
                file = workspace.child(param.getName().substring(5))
                file.getParent().mkdirs()
                file.write(param.getValue(), "UTF-8")
            } else if (param instanceof FileParameterValue && param.getFile().getSize() != 0) {
                file = workspace.child(param.getLocation())
                file.getParent().mkdirs()
                file.copyFrom(param.getFile())
            }
        }
    }
}
