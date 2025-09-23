# OData Server CI/CD Documentation

## 🎯 **Overview**

This document describes the complete CI/CD pipeline setup for the OData Server using Jenkins and Docker deployment.

## 📁 **File Structure**

```
.
├── Jenkinsfile                 # Main CI/CD pipeline
├── docker-compose.yml          # Docker Compose configuration
├── env.template               # Environment variables template
├── scripts/
│   └── docker-deploy.sh       # Manual Docker deployment script
└── server/
    ├── Dockerfile             # Docker image build
    ├── build.gradle           # Application build
    └── src/                   # Source code
```

## 🚀 **Jenkins Pipeline Features**

### **Stages:**
1. **🔍 Checkout & Setup** - Clean workspace, checkout code
2. **📋 Environment Validation** - Validate Java, Gradle, Docker
3. **🔨 Build Application** - Clean build with Gradle
4. **🧪 Run Tests** - Unit & integration tests (optional skip)
5. **🔍 Code Quality Analysis** - SonarQube & security scanning
6. **🐳 Build Docker Image** - Multi-stage Docker build
7. **💾 Save Docker Image** - Archive image for deployment
8. **🚀 Deploy to Server** - Docker container deployment
9. **🧪 Post-Deployment Tests** - Health checks & API tests

### **Parameters:**
- `DEPLOY_ENVIRONMENT`: Target environment (dev/staging/production)
- `DEPLOY_HOST`: Target deployment server (localhost for local deployment)
- `HOST_PORT`: Host port to expose the application (default: 8080)
- `SKIP_TESTS`: Skip test execution
- `FORCE_DEPLOY`: Force deployment even if tests fail
- `CLEANUP_OLD_CONTAINERS`: Remove old containers and images

### **Triggers:**
- **Poll SCM**: Every 5 minutes
- **Scheduled**: Daily at 2 AM
- **Manual**: On-demand builds

## 🐳 **Docker Configuration**

### **Multi-stage Build:**
```dockerfile
# Build stage
FROM gradle:8.4-jdk21 AS build
COPY . /app
WORKDIR /app/server
RUN ./gradlew clean build -x test

# Runtime stage  
FROM openjdk:21-jre-slim
COPY --from=build /app/server/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### **Image Registry:**
- **Registry**: `your-docker-registry.com`
- **Image**: `odata-server`
- **Tags**: `latest`, `v${BUILD_NUMBER}`

## ☸️ **Kubernetes Deployment**

### **Resources:**
- **Deployment**: `odata-server-deployment`
- **Service**: `odata-server-service` (LoadBalancer)
- **ConfigMap**: `odata-server-config` 
- **Secret**: `postgres-credentials`
- **Ingress**: `odata-server-ingress`
- **PodDisruptionBudget**: `odata-server-pdb`

### **Environment Configuration:**

| Environment | Replicas | CPU Request | Memory Request | CPU Limit | Memory Limit |
|-------------|----------|-------------|----------------|-----------|--------------|
| dev         | 1        | 125m        | 256Mi          | 250m      | 512Mi        |
| staging     | 2        | 250m        | 512Mi          | 500m      | 1Gi          |
| production  | 3        | 250m        | 512Mi          | 1000m     | 2Gi          |

### **Health Checks:**
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 30

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
```

## 📊 **Monitoring & Alerting**

### **Metrics Collection:**
- **Prometheus**: Service metrics collection
- **Grafana**: Dashboard visualization
- **Spring Boot Actuator**: Application metrics

### **Key Metrics:**
- HTTP request rate and response time
- JVM memory and CPU usage
- Database connection pool status
- Application health status

### **Alerts:**
- Server down (5+ minutes)
- High error rate (>10% for 5 minutes)
- High response time (>2s for 10 minutes)
- High memory usage (>80% for 10 minutes)
- Database connection pool exhausted (>90% for 5 minutes)

## 🛠️ **Setup Instructions**

### **1. Jenkins Setup**

#### **Required Plugins:**
```bash
# Install Jenkins plugins
- Docker Pipeline
- Kubernetes CLI
- SonarQube Scanner
- Slack Notification
- Email Extension
```

#### **Credentials Configuration:**
```bash
# Add these credentials to Jenkins
- docker-registry-credentials (username/password)
- k8s-config (kubeconfig file)
- postgres-credentials (username/password)
- sonarqube-token (secret text)
```

#### **Pipeline Creation:**
1. Create new Pipeline job
2. Set SCM to Git repository
3. Pipeline script from SCM
4. Script path: `Jenkinsfile`

### **2. Kubernetes Setup**

#### **Create Namespaces:**
```bash
kubectl create namespace odata-dev
kubectl create namespace odata-staging  
kubectl create namespace odata-production
kubectl create namespace monitoring
```

#### **Deploy Base Resources:**
```bash
# Deploy application
kubectl apply -f k8s/deployment.yaml

# Deploy monitoring
kubectl apply -f k8s/monitoring.yaml
```

### **3. Docker Registry Setup**

#### **Registry Authentication:**
```bash
# Create Docker registry secret
kubectl create secret docker-registry docker-registry-secret \
  --docker-server=your-docker-registry.com \
  --docker-username=your-username \
  --docker-password=your-password \
  --namespace=odata-production
```

## 🔧 **Manual Deployment**

### **1. Using Docker Deploy Script:**
```bash
# Deploy to development locally
./scripts/docker-deploy.sh -e dev -t latest -p 8080

# Deploy to production on remote server
./scripts/docker-deploy.sh -e production -t v1.2.3 -f

# Dry run deployment
./scripts/docker-deploy.sh -e staging -t latest -d

# Deploy with custom configuration
./scripts/docker-deploy.sh -e production -t latest -p 8080 -n my-odata-server
```

### **2. Using Docker Compose:**
```bash
# Setup environment variables
cp env.template .env
# Edit .env file with your configuration

# Start the service
docker-compose up -d

# View logs
docker-compose logs -f

# Stop the service
docker-compose down

# Restart with new image
docker-compose pull && docker-compose up -d
```

### **3. Using Docker directly:**
```bash
# Build image
cd server && docker build -t odata-server:latest .

# Run container
docker run -d \
  --name odata-server-container \
  --restart unless-stopped \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=production \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://54.169.24.95:5432/mydb \
  -e SPRING_DATASOURCE_USERNAME=admin \
  -e SPRING_DATASOURCE_PASSWORD=secret \
  -e ODATA_DATABASE_SCHEMA=fec_csm \
  odata-server:latest

# View logs
docker logs -f odata-server-container

# Stop container
docker stop odata-server-container && docker rm odata-server-container
```

## 🐛 **Troubleshooting**

### **Common Issues:**

#### **Build Failures:**
```bash
# Check Gradle build
cd server && ./gradlew clean build --info

# Check Docker build
docker build -t odata-server:test server/

# Check Java version
java -version
```

#### **Deployment Issues:**
```bash
# Check pod status
kubectl get pods -n odata-production

# View pod logs
kubectl logs <pod-name> -n odata-production

# Describe pod for events
kubectl describe pod <pod-name> -n odata-production

# Check service endpoints
kubectl get endpoints -n odata-production
```

#### **Database Connection:**
```bash
# Test database connectivity from pod
kubectl exec -it <pod-name> -n odata-production -- /bin/bash
curl -v telnet://54.169.24.95:5432

# Check database credentials
kubectl get secret postgres-credentials -n odata-production -o yaml
```

### **Rollback Deployment:**
```bash
# Rollback to previous version
kubectl rollout undo deployment/odata-server-deployment -n odata-production

# Rollback to specific revision
kubectl rollout undo deployment/odata-server-deployment --to-revision=2 -n odata-production

# Check rollout history
kubectl rollout history deployment/odata-server-deployment -n odata-production
```

## 📞 **Support & Contacts**

- **DevOps Team**: devops@yourcompany.com
- **Development Team**: dev-team@yourcompany.com
- **Slack Channel**: #odata-server-support
- **Documentation**: [Internal Wiki Link]

## 🔗 **Useful Links**

- **Jenkins Dashboard**: http://jenkins.yourcompany.com
- **Grafana Dashboards**: http://grafana.yourcompany.com
- **SonarQube**: http://sonarqube.yourcompany.com
- **Docker Registry**: http://registry.yourcompany.com
- **Production OData Endpoint**: http://nguyenthanhlong.info/odata/

## 📝 **Change Log**

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-09-23 | Initial CI/CD pipeline setup |
| 1.1.0 | TBD | Add blue-green deployment |
| 1.2.0 | TBD | Add canary deployment strategy |
