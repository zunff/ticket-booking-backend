#!/bin/bash

set -e

echo "=========================================="
echo "Deploying Ticket Booking System to K8s"
echo "=========================================="

echo ""
echo "Step 1: Building Maven project..."
mvn clean package -DskipTests

echo ""
echo "Step 2: Building Docker images..."

echo "  - Building user-service..."
docker build -t user-service:latest -f user-service/Dockerfile user-service

echo "  - Building ticket-service..."
docker build -t ticket-service:latest -f ticket-service/Dockerfile ticket-service

echo ""
echo "Step 3: Creating namespace..."
kubectl apply -f k8s/namespace.yaml

echo ""
echo "Step 4: Deploying microservices..."

echo "  - Creating user-service..."
kubectl apply -f k8s/user-service.yaml

echo "  - Creating ticket-service..."
kubectl apply -f k8s/ticket-service.yaml

echo ""
echo "Step 5: Waiting for microservices to be ready..."
kubectl rollout status deployment/user-service -n ticket-booking --timeout=180s || true
kubectl rollout status deployment/ticket-service -n ticket-booking --timeout=180s || true

echo ""
echo "=========================================="
echo "Deployment Complete!"
echo "=========================================="
echo ""
echo "Prerequisites:"
echo "  - MySQL, Redis, Kafka should be running via: docker compose -f docker-compose.dev.yaml up -d"
echo ""
echo "To access the services:"
echo "  kubectl port-forward svc/ticket-service -n ticket-booking 8080:8080"
echo "  kubectl port-forward svc/user-service -n ticket-booking 8081:8081"
echo ""
echo "To check pod status:"
echo "  kubectl get pods -n ticket-booking"
echo ""
echo "To view logs:"
echo "  kubectl logs -f deployment/ticket-service -n ticket-booking"
echo "  kubectl logs -f deployment/user-service -n ticket-booking"
echo ""
