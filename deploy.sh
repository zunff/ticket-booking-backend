#!/bin/bash

set -e

echo "=========================================="
echo "Building and Deploying Ticket Booking System"
echo "=========================================="

echo ""
echo "Step 1: Building Docker images..."

echo "  - Building user-service..."
docker build -t user-service:latest -f user-service/Dockerfile .

echo "  - Building ticket-service..."
docker build -t ticket-service:latest -f ticket-service/Dockerfile .

echo ""
echo "Step 2: Deploying infrastructure to Kubernetes..."

echo "  - Creating MySQL..."
kubectl apply -f k8s/mysql.yaml

echo "  - Creating Redis..."
kubectl apply -f k8s/redis.yaml

echo "  - Creating RabbitMQ..."
kubectl apply -f k8s/rabbitmq.yaml

echo ""
echo "Step 3: Waiting for infrastructure to be ready..."
kubectl wait --for=condition=ready pod -l app=nacos --timeout=180s || true
kubectl wait --for=condition=ready pod -l app=mysql --timeout=180s || true
kubectl wait --for=condition=ready pod -l app=redis --timeout=180s || true
kubectl wait --for=condition=ready pod -l app=rabbitmq --timeout=180s || true

echo ""
echo "Waiting additional 30 seconds for services to stabilize..."
sleep 30

echo ""
echo "Step 4: Deploying microservices..."

echo "  - Creating user-service..."
kubectl apply -f k8s/user-service.yaml

echo "  - Creating ticket-service..."
kubectl apply -f k8s/ticket-service.yaml

echo ""
echo "Step 5: Waiting for microservices to be ready..."
kubectl rollout status deployment/user-service --timeout=180s || true
kubectl rollout status deployment/ticket-service --timeout=180s || true

echo ""
echo "=========================================="
echo "Deployment Complete!"
echo "=========================================="
echo ""
echo "To access the services:"
echo "  kubectl port-forward svc/ticket-service 8080:8080"
echo "  kubectl port-forward svc/user-service 8081:8081"
echo "  kubectl port-forward svc/nacos 8848:8848"
echo ""
echo "To check pod status:"
echo "  kubectl get pods"
echo ""
echo "To view logs:"
echo "  kubectl logs -f deployment/ticket-service"
echo "  kubectl logs -f deployment/user-service"
echo ""
