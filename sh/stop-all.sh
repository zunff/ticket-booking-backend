#!/bin/bash

echo "=========================================="
echo "  停止所有微服务"
echo "=========================================="

pkill -f "ticket-user-service-1.0.0.jar" 2>/dev/null
pkill -f "ticket-ticket-service-1.0.0.jar" 2>/dev/null
pkill -f "ticket-order-service-1.0.0.jar" 2>/dev/null
pkill -f "ticket-stock-service-1.0.0.jar" 2>/dev/null
pkill -f "ticket-gateway-service-1.0.0.jar" 2>/dev/null

echo "✅ 所有服务已停止"
