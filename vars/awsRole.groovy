
/**
* Run a closure within an assumed AWS role
*
* @param account the target account ID
* @param role the target role name to be assumed
* @param sessionName (optional) session name to identify the current session
*/
void call(String account, String role, String sessionName="jenkins-session", Closure cl) {

    String roleArn = "arn:aws:iam::${account}:role/${role}"

    // Piping multiple commands together
    // The awk call is wrapped in triple quotes to avoid \ escapes
    String awsSTSCommand = [
        "aws sts assume-role --output text --role-arn ${roleArn} --role-session-name ${sessionName}",
        "grep CREDENTIALS",
        '''awk  '{printf "AWS_ACCESS_KEY_ID=%s AWS_SECRET_ACCESS_KEY=%s AWS_SESSION_TOKEN=%s", $2, $4, $5}' '''
    ].join(' | ')

    String data = sh(script: awsSTSCommand , returnStdout: true)
    List<String> creds = data.split(' ')
    withEnv(creds) {
        cl()
    }
}
