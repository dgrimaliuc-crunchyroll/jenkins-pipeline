gitCredsId = 'e07c6884-0ed5-4b8c-a2c0-47e10d440545'
pypiCredsId = 'pypi'

node('master') {
  stage('Checkout') {
    git credentialsId: gitCredsId, url: 'git@github.com:crunchyroll/ef-open.git'
    sh 'git reset --hard'
    sh 'git clean -ffdx'
    script {
      IS_GIT_TAG = sh(script: "git tag -l --points-at HEAD", returnStdout: true).trim() != ''
    }
  }

  stage('Setup') {
    sh """
    virtualenv --python=python2.7 _build_env
    . _build_env/bin/activate
    pip2 install --upgrade pip setuptools wheel twine
    pip2 install -e '.[test]'
    pip2 list
    """
  }

  stage('Lint') {
    sh """
    . _build_env/bin/activate
    pylint --rcfile=./pylintrc --errors-only --exit-zero ./efopen
    """
  }

  stage('Test') {
    sh """
    . _build_env/bin/activate
    python2 setup.py test
    """
  }

  stage('Package') {
    sh """
    . _build_env/bin/activate
    python2 setup.py sdist bdist_wheel
    """
  }
}

if (IS_GIT_TAG) {
  stage('Publish') {
    node('master') {
      withCredentials([usernamePassword(credentialsId: pypiCredsId, passwordVariable: 'TWINE_PASSWORD', usernameVariable: 'TWINE_USERNAME')]) {
        sh """
        . _build_env/bin/activate
        ls dist/*
        twine upload --verbose --disable-progress-bar dist/*
        """
      }
    }
  }

  stage('Update') {
    // wait to make sure package is available on PyPi
    sleep 60

    def nodes = getNodesByTag('universal')
    nodes.add('master')

    nodes.each { nodeName ->
      node(nodeName) {
        sh 'pip2 install ef-open --upgrade --user'
      }
    }
  }
}


@NonCPS
def getNodesByTag(label) {
  return jenkins.model.Jenkins.instance.nodes.collect { node -> node.getLabelString().contains(label) ? node.name : null }.findAll {it != null}
}
