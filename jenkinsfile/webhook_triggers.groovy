@Library('ellation') _

import com.ellation.registry.ServiceRegistryEntry

node ('universal') {
    properties([
      pipelineTriggers([
       [$class: 'GenericTrigger',
        genericVariables: [
            [key: 'ref', value: '$.ref'],
            [key: 'repo', value: '$.repository.full_name']
        ],
        causeString: 'Triggered by $repo',
        token: env.GITHUB_DEPLOY_HOOK_TOKEN,
        printContributedVariables: true,
        printPostContent: true,
        silentResponse: true,
        // Run only when triggered by a master push
        regexpFilterText: '$ref',
        regexpFilterExpression: '^refs/heads/master$'
       ]
      ])
    ])
    git changelog: false, credentialsId: "$env.ETP_SSH_KEY_CREDENTIALS_ID", poll: false, url: "$env.ELLATION_FORMATION_REPO_URL"

    ServiceRegistryEntry entry = serviceRegistry.getMainServiceByRepoJson('service_registry.json', repo)

    def service = entry.service

    currentBuild.description = service
    build(job: "${service}-pipeline-staging", wait: false)
}
