#!/bin/bash

echo "=========================================="
echo "Stopping services and cleaning up..."
echo "=========================================="

echo ""
echo "1. Deleting Kubernetes resources..."
kubectl delete -f k8s/ticket-service.yaml --ignore-not-found
kubectl delete -f k8s/user-service.yaml --ignore-not-found
kubectl delete -f k8s/rabbitmq.yaml --ignore-not-found
kubectl delete -f k8s/redis.yaml --ignore-not-found
kubectl delete -f k8s/mysql.yaml --ignore-not-found
kubectl delete secret app-secret --ignore-not-found

echo ""
echo "2. Waiting for pods to terminate..."
sleep 5
kubectl get pods

echo ""
echo "3. Removing Docker images..."
docker rmi ticket-service:latest 2>/dev/null && echo "Removed ticket-service:latest" || echo "ticket-service:latest not found"
docker rmi user-service:latest 2>/dev/null && echo "Removed user-service:latest" || echo "user-service:latest not found"

echo ""
echo "4. Cleaning up dangling images..."
docker image prune -f

echo ""
echo "5. Listing remaining images..."
docker images | grep -E "ticket|user" || echo "No project images remaining"

echo ""
echo "=========================================="
echo "Cleanup completed!"
echo "=========================================="
