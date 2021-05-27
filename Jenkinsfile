@Library('hibernate-jenkins-pipeline-helpers@1.5') _

pipeline {
    agent {
        label 'Worker'
    }
    tools {
        maven 'Apache Maven 3.6'
        jdk 'OpenJDK 11 Latest'
    }
    stages {
        stage('Build') {
            steps {
                checkout scm
                sh """ \
                    ./mvnw -B clean verify
                """
            }
        }
        stage('Deploy image') {
            when {
                beforeAgent true
                not { changeRequest() }
            }
            environment {
                QUAY_CREDS = credentials('hibernate.quay.io')
            }
            steps {
                script {
                    if ( env.BRANCH_NAME == 'main' ) {
                        env.QUARKUS_CONTAINER_IMAGE_ADDITIONAL_TAGS = 'main,latest'
                    }
                    else {
                        env.QUARKUS_CONTAINER_IMAGE_ADDITIONAL_TAGS = env.BRANCH_NAME
                    }
                }
                sh '''
                    ./mvnw -B package -Dquarkus.container-image.build=true -Dquarkus.container-image.push=true \
                            -Dquarkus.container-image.username=${QUAY_CREDS_USR} \
                            -Dquarkus.container-image.password=${QUAY_CREDS_PSW} \
                '''
            }
        }
        stage('Deploy container') {
            when {
                beforeAgent true
                not { changeRequest() }
                branch 'main'
            }
            steps {
                // Bots are hosted on the same machine as in.relation.to
                sshagent(['jenkins.in.relation.to']) {
                    // Pull the latest version of the container image and restart the container
                    sh 'ssh in.relation.to sudo systemctl start podman-auto-update'
                }
            }
        }
    }
    post {
        always {
            // Space-separated
            notifyBuildResult maintainers: 'yoann@hibernate.org'
        }
    }
}
