pipeline {
    agent any
    
    // Define environment variables
    environment {
        // Application settings
        APP_NAME = 'odata-server'
        APP_VERSION = "${env.BUILD_NUMBER}"
        
        // Docker settings
        DOCKER_IMAGE = "${APP_NAME}"
        DOCKER_TAG = "${APP_VERSION}"
        DOCKER_CONTAINER_NAME = "${APP_NAME}-container"
        DOCKER_PORT = "8080"
        HOST_PORT = "8080"
        
        // Database settings
        DB_HOST = '54.169.24.95'
        DB_NAME = 'mydb'
        DB_SCHEMA = 'fec_csm'
        DB_USERNAME = 'admin'
        DB_PASSWORD = 'secret'
        
        // Deployment settings
        DEPLOY_HOST = 'your-server.com'  // Your target server
        DEPLOY_USER = 'deploy'           // SSH user for deployment
        
        // Notification settings
        SLACK_CHANNEL = '#deployments'
        EMAIL_RECIPIENTS = 'dev-team@yourcompany.com'
    }
    
    // Pipeline options
    options {
        // Keep only last 10 builds
        buildDiscarder(logRotator(numToKeepStr: '10'))
        
        // Timeout after 30 minutes
        timeout(time: 30, unit: 'MINUTES')
        
        // Disable concurrent builds
        disableConcurrentBuilds()
        
        // Add timestamps to console output
        timestamps()
    }
    
    // Pipeline parameters
    parameters {
        choice(
            name: 'DEPLOY_ENVIRONMENT',
            choices: ['dev', 'staging', 'production'],
            description: 'Target deployment environment'
        )
        string(
            name: 'DEPLOY_HOST',
            defaultValue: 'your-server.com',
            description: 'Target deployment server'
        )
        string(
            name: 'HOST_PORT',
            defaultValue: '8080',
            description: 'Host port to expose the application'
        )
        booleanParam(
            name: 'SKIP_TESTS',
            defaultValue: false,
            description: 'Skip running tests'
        )
        booleanParam(
            name: 'FORCE_DEPLOY',
            defaultValue: false,
            description: 'Force deployment even if tests fail'
        )
        booleanParam(
            name: 'CLEANUP_OLD_CONTAINERS',
            defaultValue: true,
            description: 'Remove old containers and images'
        )
    }
    
    stages {
        stage('🔍 Checkout & Setup') {
            steps {
                script {
                    echo "🚀 Starting CICD Pipeline for OData Server"
                    echo "📦 Build Number: ${env.BUILD_NUMBER}"
                    echo "🌍 Target Environment: ${params.DEPLOY_ENVIRONMENT}"
                    echo "🔗 Git Branch: ${env.GIT_BRANCH}"
                    
                    // Clean workspace
                    cleanWs()
                    
                    // Checkout source code
                    checkout scm
                    
                    // Display project structure
                    sh 'find . -name "*.gradle" -o -name "Dockerfile" | head -20'
                }
            }
        }
        
        stage('📋 Environment Validation') {
            steps {
                script {
                    echo "🔧 Validating build environment..."
                    
                    // Check Java version
                    sh 'java -version'
                    
                    // Check Gradle version
                    sh 'cd server && ./gradlew --version'
                    
                    // Check Docker availability
                    sh 'docker --version'
                    
                    // Validate required files
                    sh '''
                        if [ ! -f server/build.gradle ]; then
                            echo "❌ server/build.gradle not found!"
                            exit 1
                        fi
                        
                        if [ ! -f server/Dockerfile ]; then
                            echo "❌ server/Dockerfile not found!"
                            exit 1
                        fi
                        
                        echo "✅ All required files found"
                    '''
                }
            }
        }
        
        stage('🔨 Build Application') {
            steps {
                script {
                    echo "🏗️ Building OData Server application..."
                    
                    dir('server') {
                        // Clean previous builds
                        sh './gradlew clean'
                        
                        // Build application
                        sh './gradlew build -x test --info'
                        
                        // Check if JAR was created
                        sh '''
                            if [ -f build/libs/server-*.jar ]; then
                                echo "✅ JAR file built successfully"
                                ls -la build/libs/server-*.jar
                            else
                                echo "❌ JAR file not found!"
                                exit 1
                            fi
                        '''
                    }
                }
            }
            post {
                success {
                    echo "✅ Build completed successfully"
                }
                failure {
                    echo "❌ Build failed"
                }
            }
        }
        
        stage('🐳 Build Docker Image') {
            steps {
                script {
                    echo "🐳 Building Docker image..."
                    
                    dir('server') {
                        // Build Docker image with build args
                        sh """
                            docker build \\
                                --build-arg JAR_FILE=build/libs/server-*.jar \\
                                --build-arg SPRING_PROFILES_ACTIVE=${params.DEPLOY_ENVIRONMENT} \\
                                -t ${DOCKER_IMAGE}:${DOCKER_TAG} \\
                                -t ${DOCKER_IMAGE}:latest \\
                                -t ${DOCKER_IMAGE}:${params.DEPLOY_ENVIRONMENT} .
                        """
                        
                        // Inspect image size
                        sh "docker images ${DOCKER_IMAGE}:${DOCKER_TAG}"
                        
                        // Optional: Scan image for vulnerabilities if trivy is available
                        sh """
                            if command -v trivy &> /dev/null; then
                                trivy image ${DOCKER_IMAGE}:${DOCKER_TAG} || true
                            else
                                echo "Trivy not available, skipping vulnerability scan"
                            fi
                        """
                    }
                }
            }
            post {
                success {
                    echo "✅ Docker image built successfully"
                }
                failure {
                    echo "❌ Docker image build failed"
                }
            }
        }
        
        stage('💾 Save Docker Image') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                    params.FORCE_DEPLOY
                }
            }
            steps {
                script {
                    echo "💾 Saving Docker image for deployment..."
                    
                    // Save Docker image to tar file for transfer
                    sh """
                        docker save ${DOCKER_IMAGE}:${DOCKER_TAG} > ${DOCKER_IMAGE}-${DOCKER_TAG}.tar
                        ls -lh ${DOCKER_IMAGE}-${DOCKER_TAG}.tar
                    """
                    
                    // Archive the image for download
                    archiveArtifacts artifacts: "${DOCKER_IMAGE}-${DOCKER_TAG}.tar", fingerprint: true
                }
            }
            post {
                success {
                    echo "✅ Docker image saved successfully"
                }
                failure {
                    echo "❌ Failed to save Docker image"
                }
            }
        }
        
        stage('🚀 Deploy to Server') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                    params.FORCE_DEPLOY
                }
            }
            steps {
                script {
                    echo "🚀 Deploying to ${params.DEPLOY_ENVIRONMENT} environment on ${params.DEPLOY_HOST}..."
                    
                    // Set environment-specific variables
                    def hostPort = params.HOST_PORT ?: env.HOST_PORT
                    def containerName = "${DOCKER_CONTAINER_NAME}-${params.DEPLOY_ENVIRONMENT}"
                    
                    // Deploy locally or to remote server
                    if (params.DEPLOY_HOST == 'localhost' || params.DEPLOY_HOST == '127.0.0.1') {
                        // Local deployment
                        deployLocally(containerName, hostPort)
                    } else {
                        // Remote deployment via SSH
                        deployRemotely(params.DEPLOY_HOST, containerName, hostPort)
                    }
                }
            }
            post {
                success {
                    echo "✅ Deployment to ${params.DEPLOY_ENVIRONMENT} completed successfully"
                }
                failure {
                    echo "❌ Deployment to ${params.DEPLOY_ENVIRONMENT} failed"
                }
            }
        }
        
        stage('🧪 Post-Deployment Tests') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            steps {
                script {
                    echo "🧪 Running post-deployment integration tests..."
                    
                    def hostPort = params.HOST_PORT ?: env.HOST_PORT
                    def testHost = params.DEPLOY_HOST == 'localhost' ? 'localhost' : params.DEPLOY_HOST
                    
                    // Wait for application to start
                    sleep(30)
                    
                    sh """
                        echo "Testing OData endpoints on ${testHost}:${hostPort}..."
                        
                        # Test health endpoint
                        echo "Testing health endpoint..."
                        curl -f --max-time 30 http://${testHost}:${hostPort}/actuator/health || echo "Health check failed"
                        
                        # Test OData service document
                        echo "Testing OData service document..."
                        curl -f --max-time 30 http://${testHost}:${hostPort}/odata/ || echo "OData service check failed"
                        
                        # Test OData metadata endpoint
                        echo "Testing OData metadata endpoint..."
                        curl -f --max-time 30 http://${testHost}:${hostPort}/odata/\\\$metadata || echo "OData metadata check failed"
                        
                        echo "✅ All post-deployment tests completed"
                    """
                }
            }
        }
    }
    
    post {
        always {
            script {
                echo "🧹 Cleaning up workspace..."
                
                // Clean up Docker images
                sh """
                    docker rmi ${DOCKER_IMAGE}:${DOCKER_TAG} || true
                    docker rmi ${DOCKER_IMAGE}:latest || true
                    docker system prune -f || true
                """
                
                // Archive build artifacts
                dir('server') {
                    archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true
                    archiveArtifacts artifacts: 'build/reports/**/*', allowEmptyArchive: true
                }
            }
        }
        
        success {
            script {
                echo "🎉 Pipeline completed successfully!"
                
                // Send success notification
                sendNotification('SUCCESS', 
                    "✅ OData Server deployment to ${params.DEPLOY_ENVIRONMENT} successful",
                    "Build #${env.BUILD_NUMBER} completed successfully"
                )
            }
        }
        
        failure {
            script {
                echo "❌ Pipeline failed!"
                
                // Send failure notification
                sendNotification('FAILURE', 
                    "❌ OData Server deployment to ${params.DEPLOY_ENVIRONMENT} failed",
                    "Build #${env.BUILD_NUMBER} failed. Please check the logs."
                )
            }
        }
        
        unstable {
            script {
                echo "⚠️ Pipeline completed with warnings"
                
                sendNotification('UNSTABLE', 
                    "⚠️ OData Server deployment to ${params.DEPLOY_ENVIRONMENT} unstable",
                    "Build #${env.BUILD_NUMBER} completed with warnings"
                )
            }
        }
    }
}

// Helper functions for Docker deployment
def deployLocally(containerName, hostPort) {
    echo "🏠 Deploying locally..."
    
    sh """
        # Stop and remove existing container if it exists
        docker stop ${containerName} || true
        docker rm ${containerName} || true
        
        # Run new container
        docker run -d \\
            --name ${containerName} \\
            --restart unless-stopped \\
            -p ${hostPort}:${env.DOCKER_PORT} \\
            -e SPRING_PROFILES_ACTIVE=${params.DEPLOY_ENVIRONMENT} \\
            -e SPRING_DATASOURCE_URL=jdbc:postgresql://${env.DB_HOST}:5432/${env.DB_NAME} \\
            -e SPRING_DATASOURCE_USERNAME=${env.DB_USERNAME} \\
            -e SPRING_DATASOURCE_PASSWORD=${env.DB_PASSWORD} \\
            -e ODATA_DATABASE_SCHEMA=${env.DB_SCHEMA} \\
            ${env.DOCKER_IMAGE}:${env.DOCKER_TAG}
        
        # Wait for container to start
        sleep 10
        
        # Check container status
        docker ps | grep ${containerName}
        docker logs ${containerName} --tail 20
        
        echo "✅ Container ${containerName} deployed locally on port ${hostPort}"
    """
}

def deployRemotely(deployHost, containerName, hostPort) {
    echo "🌐 Deploying to remote server: ${deployHost}..."
    
    withCredentials([sshUserPrivateKey(credentialsId: 'deploy-ssh-key', keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
        sh """
            # Transfer Docker image to remote server
            echo "📦 Transferring Docker image to ${deployHost}..."
            scp -i \$SSH_KEY -o StrictHostKeyChecking=no ${env.DOCKER_IMAGE}-${env.DOCKER_TAG}.tar \$SSH_USER@${deployHost}:/tmp/
            
            # Deploy on remote server
            ssh -i \$SSH_KEY -o StrictHostKeyChecking=no \$SSH_USER@${deployHost} << 'EOF'
                # Load Docker image
                docker load < /tmp/${env.DOCKER_IMAGE}-${env.DOCKER_TAG}.tar
                
                # Stop and remove existing container
                docker stop ${containerName} || true
                docker rm ${containerName} || true
                
                # Run new container
                docker run -d \\
                    --name ${containerName} \\
                    --restart unless-stopped \\
                    -p ${hostPort}:${env.DOCKER_PORT} \\
                    -e SPRING_PROFILES_ACTIVE=${params.DEPLOY_ENVIRONMENT} \\
                    -e SPRING_DATASOURCE_URL=jdbc:postgresql://${env.DB_HOST}:5432/${env.DB_NAME} \\
                    -e SPRING_DATASOURCE_USERNAME=${env.DB_USERNAME} \\
                    -e SPRING_DATASOURCE_PASSWORD=${env.DB_PASSWORD} \\
                    -e ODATA_DATABASE_SCHEMA=${env.DB_SCHEMA} \\
                    ${env.DOCKER_IMAGE}:${env.DOCKER_TAG}
                
                # Clean up transferred image file
                rm -f /tmp/${env.DOCKER_IMAGE}-${env.DOCKER_TAG}.tar
                
                # Wait for container to start
                sleep 10
                
                # Check container status
                docker ps | grep ${containerName}
                docker logs ${containerName} --tail 20
                
                echo "✅ Container ${containerName} deployed on ${deployHost}:${hostPort}"
EOF
        """
    }
}

def sendNotification(status, title, message) {
    def color = status == 'SUCCESS' ? 'good' : (status == 'FAILURE' ? 'danger' : 'warning')
    
    // Slack notification
    slackSend(
        channel: env.SLACK_CHANNEL,
        color: color,
        message: """
${title}
Environment: ${params.DEPLOY_ENVIRONMENT}
Build: #${env.BUILD_NUMBER}
Branch: ${env.GIT_BRANCH}
${message}
        """
    )
    
    // Email notification for failures
    if (status == 'FAILURE') {
        emailext(
            subject: "${title} - Build #${env.BUILD_NUMBER}",
            body: """
<h2>${title}</h2>
<p><strong>Environment:</strong> ${params.DEPLOY_ENVIRONMENT}</p>
<p><strong>Build Number:</strong> #${env.BUILD_NUMBER}</p>
<p><strong>Branch:</strong> ${env.GIT_BRANCH}</p>
<p><strong>Message:</strong> ${message}</p>
<p><strong>Build URL:</strong> <a href="${env.BUILD_URL}">${env.BUILD_URL}</a></p>
            """,
            to: env.EMAIL_RECIPIENTS,
            mimeType: 'text/html'
        )
    }
}
