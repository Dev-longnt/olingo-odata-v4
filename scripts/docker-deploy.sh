#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
ENVIRONMENT="dev"
IMAGE_TAG="latest"
HOST_PORT="8080"
CONTAINER_PORT="8080"
CONTAINER_NAME="odata-server"
DRY_RUN=false
FORCE=false
CLEANUP=true

# Database configuration
DB_HOST="54.169.24.95"
DB_NAME="mydb"
DB_SCHEMA="fec_csm"
DB_USERNAME="admin"
DB_PASSWORD="secret"

# Print usage
usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Deploy OData Server using Docker

OPTIONS:
    -e, --environment ENV    Target environment (dev|staging|production). Default: dev
    -t, --tag TAG           Docker image tag. Default: latest
    -p, --port PORT         Host port to bind. Default: 8080
    -n, --name NAME         Container name. Default: odata-server
    -h, --host HOST         Database host. Default: 54.169.24.95
    -d, --dry-run           Show commands without executing
    -f, --force             Force deployment without confirmation
    -c, --no-cleanup        Don't remove old containers
    --help                  Show this help message

EXAMPLES:
    $0 -e production -t v1.2.3 -p 8080
    $0 --environment staging --tag latest --dry-run
    $0 -e dev -f --no-cleanup
    
EOF
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -e|--environment)
            ENVIRONMENT="$2"
            shift 2
            ;;
        -t|--tag)
            IMAGE_TAG="$2"
            shift 2
            ;;
        -p|--port)
            HOST_PORT="$2"
            shift 2
            ;;
        -n|--name)
            CONTAINER_NAME="$2"
            shift 2
            ;;
        -h|--host)
            DB_HOST="$2"
            shift 2
            ;;
        -d|--dry-run)
            DRY_RUN=true
            shift
            ;;
        -f|--force)
            FORCE=true
            shift
            ;;
        -c|--no-cleanup)
            CLEANUP=false
            shift
            ;;
        --help)
            usage
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            usage
            exit 1
            ;;
    esac
done

# Set container name with environment suffix
FULL_CONTAINER_NAME="${CONTAINER_NAME}-${ENVIRONMENT}"
IMAGE_NAME="odata-server:${IMAGE_TAG}"

echo -e "${BLUE}🐳 OData Server Docker Deployment${NC}"
echo -e "${BLUE}=================================${NC}"
echo -e "Environment: ${YELLOW}$ENVIRONMENT${NC}"
echo -e "Image: ${YELLOW}$IMAGE_NAME${NC}"
echo -e "Container: ${YELLOW}$FULL_CONTAINER_NAME${NC}"
echo -e "Port: ${YELLOW}$HOST_PORT:$CONTAINER_PORT${NC}"
echo -e "Database: ${YELLOW}$DB_HOST${NC}"
echo -e "Dry Run: ${YELLOW}$DRY_RUN${NC}"
echo ""

# Validate Docker
echo -e "${BLUE}🔍 Validating Docker environment...${NC}"
if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker is not installed or not in PATH${NC}"
    exit 1
fi

if ! docker ps &> /dev/null; then
    echo -e "${RED}❌ Cannot connect to Docker daemon${NC}"
    exit 1
fi

# Check if image exists
echo -e "${BLUE}🔍 Checking Docker image...${NC}"
if ! docker image inspect "$IMAGE_NAME" &> /dev/null; then
    echo -e "${RED}❌ Docker image $IMAGE_NAME not found${NC}"
    echo -e "${YELLOW}Available images:${NC}"
    docker images | grep odata-server | head -5
    exit 1
fi

# Check port availability
echo -e "${BLUE}🔍 Checking port availability...${NC}"
if netstat -tuln 2>/dev/null | grep ":$HOST_PORT " > /dev/null; then
    EXISTING_CONTAINER=$(docker ps --filter "publish=$HOST_PORT" --format "{{.Names}}" | head -1)
    if [[ -n "$EXISTING_CONTAINER" && "$EXISTING_CONTAINER" != "$FULL_CONTAINER_NAME" ]]; then
        echo -e "${YELLOW}⚠️ Port $HOST_PORT is already in use by container: $EXISTING_CONTAINER${NC}"
        if [[ "$FORCE" == "false" ]]; then
            read -p "Continue anyway? (y/N): " -n 1 -r
            echo
            if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                echo -e "${YELLOW}⏹️ Deployment cancelled${NC}"
                exit 0
            fi
        fi
    fi
fi

# Environment-specific configuration
case $ENVIRONMENT in
    dev)
        MEMORY_LIMIT="512m"
        CPU_LIMIT="0.5"
        RESTART_POLICY="unless-stopped"
        ;;
    staging)
        MEMORY_LIMIT="1g"
        CPU_LIMIT="1.0"
        RESTART_POLICY="unless-stopped"
        ;;
    production)
        MEMORY_LIMIT="2g"
        CPU_LIMIT="2.0"
        RESTART_POLICY="always"
        ;;
    *)
        MEMORY_LIMIT="512m"
        CPU_LIMIT="0.5"
        RESTART_POLICY="unless-stopped"
        ;;
esac

echo -e "${BLUE}📋 Deployment Configuration${NC}"
echo -e "${BLUE}===========================${NC}"
echo -e "Memory Limit: ${YELLOW}$MEMORY_LIMIT${NC}"
echo -e "CPU Limit: ${YELLOW}$CPU_LIMIT${NC}"
echo -e "Restart Policy: ${YELLOW}$RESTART_POLICY${NC}"
echo ""

# Confirmation prompt
if [[ "$FORCE" == "false" && "$DRY_RUN" == "false" ]]; then
    echo -e "${YELLOW}⚠️ This will deploy OData Server to $ENVIRONMENT environment${NC}"
    read -p "Continue? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${YELLOW}⏹️ Deployment cancelled${NC}"
        exit 0
    fi
fi

# Build Docker commands
DOCKER_RUN_CMD="docker run -d \\
    --name $FULL_CONTAINER_NAME \\
    --restart $RESTART_POLICY \\
    --memory $MEMORY_LIMIT \\
    --cpus $CPU_LIMIT \\
    -p $HOST_PORT:$CONTAINER_PORT \\
    -e SPRING_PROFILES_ACTIVE=$ENVIRONMENT \\
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://$DB_HOST:5432/$DB_NAME \\
    -e SPRING_DATASOURCE_USERNAME=$DB_USERNAME \\
    -e SPRING_DATASOURCE_PASSWORD=$DB_PASSWORD \\
    -e ODATA_DATABASE_SCHEMA=$DB_SCHEMA \\
    -e SERVER_PORT=$CONTAINER_PORT \\
    -e JAVA_OPTS='-Xms256m -Xmx$(echo $MEMORY_LIMIT | sed 's/g/*1024/g;s/m//g' | bc)m' \\
    $IMAGE_NAME"

if [[ "$DRY_RUN" == "true" ]]; then
    echo -e "${YELLOW}🔍 Dry run mode - commands that would be executed:${NC}"
    echo ""
    
    if [[ "$CLEANUP" == "true" ]]; then
        echo -e "${BLUE}# Stop and remove existing container:${NC}"
        echo "docker stop $FULL_CONTAINER_NAME || true"
        echo "docker rm $FULL_CONTAINER_NAME || true"
        echo ""
    fi
    
    echo -e "${BLUE}# Run new container:${NC}"
    echo "$DOCKER_RUN_CMD"
    echo ""
    
    echo -e "${BLUE}# Health check:${NC}"
    echo "sleep 30"
    echo "curl -f http://localhost:$HOST_PORT/actuator/health"
    echo "curl -f http://localhost:$HOST_PORT/odata/\$metadata"
    
    exit 0
fi

# Execute deployment
echo -e "${BLUE}🚀 Starting deployment...${NC}"

# Stop and remove existing container
if [[ "$CLEANUP" == "true" ]]; then
    echo -e "${BLUE}🛑 Stopping existing container...${NC}"
    
    if docker ps -q --filter "name=$FULL_CONTAINER_NAME" | grep -q .; then
        docker stop "$FULL_CONTAINER_NAME"
        echo -e "${GREEN}✅ Container stopped${NC}"
    else
        echo -e "${YELLOW}ℹ️ Container not running${NC}"
    fi
    
    if docker ps -aq --filter "name=$FULL_CONTAINER_NAME" | grep -q .; then
        docker rm "$FULL_CONTAINER_NAME"
        echo -e "${GREEN}✅ Container removed${NC}"
    else
        echo -e "${YELLOW}ℹ️ Container does not exist${NC}"
    fi
fi

# Run new container
echo -e "${BLUE}🚀 Starting new container...${NC}"
CONTAINER_ID=$(eval $DOCKER_RUN_CMD)

if [[ -n "$CONTAINER_ID" ]]; then
    echo -e "${GREEN}✅ Container started successfully${NC}"
    echo -e "Container ID: ${YELLOW}${CONTAINER_ID:0:12}${NC}"
else
    echo -e "${RED}❌ Failed to start container${NC}"
    exit 1
fi

# Wait for application to start
echo -e "${BLUE}⏳ Waiting for application to start...${NC}"
sleep 15

# Check container status
echo -e "${BLUE}🔍 Checking container status...${NC}"
if docker ps --filter "name=$FULL_CONTAINER_NAME" --filter "status=running" | grep -q "$FULL_CONTAINER_NAME"; then
    echo -e "${GREEN}✅ Container is running${NC}"
    
    # Show container info
    docker ps --filter "name=$FULL_CONTAINER_NAME" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
else
    echo -e "${RED}❌ Container is not running${NC}"
    echo -e "${YELLOW}Container logs:${NC}"
    docker logs "$FULL_CONTAINER_NAME" --tail 20
    exit 1
fi

# Health checks
echo -e "${BLUE}🏥 Performing health checks...${NC}"

# Wait a bit more for the application to fully start
sleep 15

# Health endpoint check
if curl -f --max-time 30 --silent "http://localhost:$HOST_PORT/actuator/health" > /dev/null; then
    echo -e "${GREEN}✅ Health check passed${NC}"
else
    echo -e "${RED}❌ Health check failed${NC}"
    echo -e "${YELLOW}Container logs (last 10 lines):${NC}"
    docker logs "$FULL_CONTAINER_NAME" --tail 10
fi

# OData metadata check
if curl -f --max-time 30 --silent "http://localhost:$HOST_PORT/odata/\$metadata" > /dev/null; then
    echo -e "${GREEN}✅ OData metadata check passed${NC}"
else
    echo -e "${RED}❌ OData metadata check failed${NC}"
fi

echo ""
echo -e "${GREEN}🎉 Deployment completed!${NC}"
echo ""
echo -e "${BLUE}📊 Service Information:${NC}"
echo -e "Application URL: ${YELLOW}http://localhost:$HOST_PORT${NC}"
echo -e "Health Check: ${YELLOW}http://localhost:$HOST_PORT/actuator/health${NC}"
echo -e "OData Service: ${YELLOW}http://localhost:$HOST_PORT/odata/${NC}"
echo -e "OData Metadata: ${YELLOW}http://localhost:$HOST_PORT/odata/\$metadata${NC}"
echo ""
echo -e "${BLUE}🛠️ Useful Commands:${NC}"
echo -e "View logs: ${YELLOW}docker logs -f $FULL_CONTAINER_NAME${NC}"
echo -e "Stop container: ${YELLOW}docker stop $FULL_CONTAINER_NAME${NC}"
echo -e "Remove container: ${YELLOW}docker rm $FULL_CONTAINER_NAME${NC}"
echo -e "Container shell: ${YELLOW}docker exec -it $FULL_CONTAINER_NAME /bin/bash${NC}"
