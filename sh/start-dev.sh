#!/bin/bash

echo "=========================================="
echo "  高并发抢票系统 - 本地开发环境启动脚本"
echo "=========================================="

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

echo ""
echo "[1/4] 编译项目..."
mvn clean package -DskipTests -q
if [ $? -ne 0 ]; then
    echo "❌ 编译失败，请检查代码"
    exit 1
fi
echo "✅ 编译完成"

echo ""
echo "[2/4] 停止旧进程..."
pkill -f "ticket-user-service-1.0.0.jar" 2>/dev/null
pkill -f "ticket-service-1.0.0.jar" 2>/dev/null
pkill -f "ticket-order-service-1.0.0.jar" 2>/dev/null
pkill -f "ticket-stock-service-1.0.0.jar" 2>/dev/null
pkill -f "ticket-gateway-service-1.0.0.jar" 2>/dev/null
sleep 2
echo "✅ 旧进程已停止"

echo ""
echo "[3/4] 启动微服务..."

mkdir -p .log/service

echo "  启动 ticket-user-service (端口: 8081)..."
java -Xms128m -Xmx256m -jar ticket-user-service/target/ticket-user-service-1.0.0.jar --spring.profiles.active=dev > .log/service/user-service.log 2>&1 &
sleep 3

echo "  启动 ticket-service (端口: 8080)..."
java -Xms128m -Xmx256m -jar ticket-service/target/ticket-service-1.0.0.jar --spring.profiles.active=dev > .log/service/ticket-service.log 2>&1 &
sleep 3

echo "  启动 ticket-order-service (端口: 8082)..."
java -Xms128m -Xmx256m -jar ticket-order-service/target/ticket-order-service-1.0.0.jar --spring.profiles.active=dev > .log/service/order-service.log 2>&1 &
sleep 3

echo "  启动 ticket-stock-service (端口: 8083)..."
java -Xms128m -Xmx256m -jar ticket-stock-service/target/ticket-stock-service-1.0.0.jar --spring.profiles.active=dev > .log/service/stock-service.log 2>&1 &
sleep 3

echo "  启动 ticket-gateway-service (端口: 9000)..."
java -Xms128m -Xmx256m -jar ticket-gateway-service/target/ticket-gateway-service-1.0.0.jar --spring.profiles.active=dev > .log/service/gateway-service.log 2>&1 &
sleep 3

echo ""
echo "[4/4] 等待服务启动..."
sleep 10

echo ""
echo "=========================================="
echo "  服务启动完成！"
echo "=========================================="
echo ""
echo "服务地址:"
echo "  - ticket-gateway-service:    http://localhost:9000"
echo "  - ticket-user-service:       http://localhost:8081"
echo "  - ticket-service:           http://localhost:8080"
echo "  - ticket-order-service:      http://localhost:8082"
echo "  - ticket-stock-service:      http://localhost:8083"
echo ""
echo "API 路由 (通过 Gateway 访问):"
echo "  - 用户登录:   POST /api/users/login"
echo "  - 票务列表:   GET  /api/tickets"
echo "  - 抢票:       POST /api/orders/book"
echo "  - 库存查询:   GET  /api/stock/{ticketId}"
echo ""
echo "Admin API (需要管理员权限):"
echo "  - 场次管理:   /api/admin/tickets/**"
echo "  - 订单管理:   /api/admin/orders/**"
echo "  - 库存管理:   /api/admin/stock/**"
echo ""
echo "日志目录: $PROJECT_DIR/.log/service/"
echo ""
echo "停止服务: ./sh/stop-all.sh"
echo "=========================================="
