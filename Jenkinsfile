pipeline {
    agent any

    environment {
        APP_NAME    = 'cloud-health-monitor'
        MAVEN_HOME  = '/tmp/maven'
        JAVA_HOME   = '/usr/lib/jvm/java-21-openjdk-amd64'
        PATH        = "${MAVEN_HOME}/bin:${JAVA_HOME}/bin:${env.PATH}"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        timeout(time: 20, unit: 'MINUTES')
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                echo "Branch: ${env.GIT_BRANCH}"
                echo "Commit: ${env.GIT_COMMIT}"
            }
        }

        stage('Clean') {
            steps {
                dir('cloud-health-monitor') {
                    sh 'mvn clean'
                }
            }
        }

        stage('Compile') {
            steps {
                dir('cloud-health-monitor') {
                    sh 'mvn compile -q'
                }
            }
        }

        stage('Test') {
            steps {
                dir('cloud-health-monitor') {
                    sh 'mvn test'
                }
            }
            post {
                always {
                    junit 'cloud-health-monitor/target/surefire-reports/*.xml'
                }
                failure {
                    echo 'Tests FAILED — pipeline will not proceed to package stage'
                }
            }
        }

        stage('Package') {
            steps {
                dir('cloud-health-monitor') {
                    sh 'mvn package -DskipTests -q'
                }
            }
        }

        stage('Archive Artifact') {
            steps {
                archiveArtifacts artifacts: 'cloud-health-monitor/target/*.jar',
                                 fingerprint: true,
                                 allowEmptyArchive: false
                echo "Artifact archived — Build #${env.BUILD_NUMBER}"
            }
        }
    }

    post {
        success {
            echo "✅ Pipeline SUCCESS — Build #${env.BUILD_NUMBER}"
        }
        failure {
            echo "❌ Pipeline FAILED — Build #${env.BUILD_NUMBER}"
        }
        always {
            cleanWs()
        }
    }
}
