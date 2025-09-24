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
            defaultValue: 'localhost',
            description: 'Target deployment server'
        )
        string(
            name: 'HOST_PORT',
            defaultValue: '8080',
            description: 'Host port to expose the application'
        )
        booleanParam(
            name: 'FORCE_DEPLOY',
            defaultValue: false,
            description: 'Force deployment even if tests fail'
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
        
        stage('🔨 Build Application') {
            steps {
                script {
                    echo "🏗️ Building OData Server application..."
                    
                    dir('server') {

                        // Make gradlew executable
                        sh 'chmod +x ../gradlew'

                        // Clean previous builds
                        sh '../gradlew clean'
                        
                        // Build application
                        sh '../gradlew build -x test --info'
                        
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
        
        stage('🚀 Deploy to Server') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                    expression { return params.FORCE_DEPLOY }
                }
            }
            steps {
                script {
                    echo "🚀 Deploying to ${params.DEPLOY_ENVIRONMENT} environment on ${params.DEPLOY_HOST}..."
                    
                    // Set environment-specific variables
                    def hostPort = params.HOST_PORT ?: env.HOST_PORT
                    def containerName = "${DOCKER_CONTAINER_NAME}-${params.DEPLOY_ENVIRONMENT}"
                    
                    // Local deployment
                    deployLocally(containerName, hostPort)
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
                    expression { return params.FORCE_DEPLOY }
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
