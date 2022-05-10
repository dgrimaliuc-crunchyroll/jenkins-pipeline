#!/usr/bin/env groovy

def test() {
  runTests = {
    sh 'export FASTLANE_SHARED_USE_LOCAL_RESOURCES=true && fastlane test skip_dependencies:true'
  }
  // Should tests fail the whole build?
  if (env.TESTS_FAIL_BUILD != 'false') {
    runTests()
  } else {
    try {
      runTests()
    } catch(err) {
    }
  }
}

def call(String agentName) {
  // Get configuration used in `fastlane` calls based on Jenkins Job name
  configuration = {
    switch (env.JOB_NAME) {
      case ~/.*Alpha.*/:
        return 'alpha'
      case ~/.*Beta.*/:
        return 'beta'
      case ~/.*Release.*/:
        return 'release'
    }
  }()

  pipeline {
    agent { label agentName }
    options {
      ansiColor('xterm')
      timestamps()
    }
    environment {
      LC_ALL      = 'en_US.UTF-8'
      LANG        = 'en_US.UTF-8'
      PATH        = "~/.rbenv/shims:${PATH}"
    }
    stages {
      stage('Build') {
        steps {
          sh "fastlane ${configuration} submit:false"
          archiveArtifacts '*.ipa'
        }
      }
      stage('Test') {
        steps {
          test()
        }
      }
      stage('Delivery') {
        when {
          // Only deliver when DELIVER variable in JENKINS is not set to false (when unchecking the box)
          expression { env.DELIVER != 'false' }
        }
        steps {
          wrap([$class: 'BuildUser']) {
            sh "export FASTLANE_SHARED_USE_LOCAL_RESOURCES=true && fastlane ${configuration} deliver_only:true skip_dependencies:true"
          }
        }
      }
    }
  }
}
