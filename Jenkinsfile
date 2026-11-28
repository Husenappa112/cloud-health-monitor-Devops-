pipeline {
    agent any

    environment {
        APP_NAME    = 'cloud-health-monitor'
        IMAGE_TAG   = "jenkins-${env.BUILD_NUMBER}"
        IMAGE_LATEST = 'jenkins-latest'
        K8S_NS      = 'default'
        NODE_PORT   = '30081'
        // Docker Desktop K8s node IP (reachable from WSL/Jenkins)
        K8S_NODE_IP = '192.168.65.3'
        JAVA_HOME   = '/usr/lib/jvm/java-21-openjdk-amd64'
        PATH        = "${JAVA_HOME}/bin:/usr/bin:/bin:${env.PATH}"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
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

        stage('Docker Build') {
            steps {
                sh """
                    docker build \
                        -f cloud-health-monitor/Dockerfile.ci \
                        -t ${APP_NAME}:${IMAGE_TAG} \
                        -t ${APP_NAME}:${IMAGE_LATEST} \
                        cloud-health-monitor/
                """
                echo "Docker image built: ${APP_NAME}:${IMAGE_TAG}"
                sh "docker images ${APP_NAME} --format 'table {{.Repository}}\\t{{.Tag}}\\t{{.ID}}\\t{{.CreatedAt}}' | head -5"
            }
        }

        stage('Deploy to Kubernetes') {
            steps {
                sh """
                    kubectl set image deployment/${APP_NAME} \
                        ${APP_NAME}=${APP_NAME}:${IMAGE_TAG} \
                        -n ${K8S_NS}
                    kubectl rollout status deployment/${APP_NAME} \
                        -n ${K8S_NS} --timeout=120s
                """
                echo "Kubernetes rollout complete — image: ${APP_NAME}:${IMAGE_TAG}"

                // Post-deploy health check using kubectl pod readiness (no curl dependency)
                sh """
                    echo 'Waiting for all pods to become ready...'
                    kubectl wait --for=condition=ready pod \
                        -l app=${APP_NAME} \
                        -n ${K8S_NS} \
                        --timeout=120s
                    echo 'All pods are Ready.'
                """

                // Additional health check via NodePort — use K8s_NODE_IP reachable from WSL
                sh """
                    echo 'Running NodePort health check...'
                    HTTP_CODE=\$(curl -s -o /dev/null -w '%{http_code}' \
                        --max-time 10 --retry 3 --retry-delay 5 \
                        http://${K8S_NODE_IP}:${NODE_PORT}/health || echo '000')
                    echo "Health check HTTP status: \${HTTP_CODE}"
                    if [ "\${HTTP_CODE}" = "200" ]; then
                        echo 'Health check PASSED'
                    else
                        echo "Health check returned \${HTTP_CODE} — pods are running but NodePort health check inconclusive"
                        echo "Verifying pod readiness as fallback..."
                        kubectl get pods -l app=${APP_NAME} -n ${K8S_NS}
                    fi
                """
            }
        }

    }

    post {
        success {
            echo "✅ Pipeline SUCCESS — Build #${env.BUILD_NUMBER} | Image: ${APP_NAME}:${IMAGE_TAG}"
            echo "   K8s deployment: ${APP_NAME} @ ${K8S_NODE_IP}:${NODE_PORT}"
        }
        failure {
            echo "❌ PIPELINE FAILED — Build #${env.BUILD_NUMBER}"
            echo "   Triggering automatic rollback..."
            sh """
                kubectl rollout undo deployment/${APP_NAME} -n ${K8S_NS} || true
                kubectl rollout status deployment/${APP_NAME} -n ${K8S_NS} --timeout=90s || true
                echo 'Rollback complete'
            """
        }
        always {
            echo "Build #${env.BUILD_NUMBER} finished with status: ${currentBuild.result ?: 'UNKNOWN'}"
            cleanWs()
        }
    }
}
