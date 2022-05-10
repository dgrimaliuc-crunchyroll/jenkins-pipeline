@Library('ellation') _

def jobToCloudMapping = [
    'jenkins-slave-docker-ami': [[
        templateDescription: 'jenkins docker ami',
        cloudName: 'Jenkins Docker'
    ],[
        templateDescription: 'jenkins-docker-performance-test',
        cloudName: 'Jenkins Docker'
    ],[
        templateDescription: 'jenkins-docker-load-test',
        cloudName: 'Jenkins Docker'
    ],[
        templateDescription: 'jenkins-docker-qe',
        cloudName: 'Jenkins Docker'
    ]]
]

def upstreamProjectTriggers = jobToCloudMapping.collect{ it.key }.join(',')
properties([pipelineTriggers([upstream(
        threshold: hudson.model.Result.SUCCESS,
        upstreamProjects: upstreamProjectTriggers)])])

node() {
    ec2SlaveAmi.updateCloudAMIFromUpstream(jobToCloudMapping)
}
