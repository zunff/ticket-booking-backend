#!/bin/bash

echo "=========================================="
echo "Stopping external dependencies..."
echo "=========================================="

echo ""
echo "Stopping Docker Compose services..."
docker compose -f docker-compose.dev.yaml down

echo ""
echo "Checking remaining containers..."
docker ps | grep -E "ticket-mysql|ticket-redis|ticket-rabbitmq" || echo "All dependency containers stopped"

echo ""
echo "=========================================="
echo "External dependencies stopped!"
echo "=========================================="
