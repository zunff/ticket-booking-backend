#!/bin/bash
# Sentinel Dashboard 规则初始化脚本
# 通过 curl 调用 Sentinel Dashboard REST API 批量添加规则

set -e

# ==================== 配置 ====================
DASHBOARD_URL="${SENTINEL_DASHBOARD_URL:-http://localhost:8858}"
DASHBOARD_USER="${SENTINEL_DASHBOARD_USER:-sentinel}"
DASHBOARD_PASS="${SENTINEL_DASHBOARD_PASS:-sentinel}"
OUTPUT_DIR="docs"
OUTPUT_FILE="${OUTPUT_DIR}/sentinel-rules-export.json"
COOKIE_FILE="/tmp/sentinel_cookie.txt"

# 服务应用名
GATEWAY_APP="ticket-gateway-service"
USER_APP="ticket-user-service"
TICKET_APP="ticket-service"
ORDER_APP="ticket-order-service"
STOCK_APP="ticket-stock-service"

# ==================== 颜色输出 ====================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# ==================== 认证函数 ====================

# 登录 Sentinel Dashboard
login_dashboard() {
    log_info "登录 Sentinel Dashboard..."

    # 清理旧 cookie
    rm -f "$COOKIE_FILE"

    # 登录获取 cookie (bladex/sentinel-dashboard 使用 /auth/login 端点，参数在 URL query 中)
    local response=$(curl -s -c "$COOKIE_FILE" -b "$COOKIE_FILE" -w "\n%{http_code}" \
        -X POST "${DASHBOARD_URL}/auth/login?username=${DASHBOARD_USER}&password=${DASHBOARD_PASS}" \
        -H "Content-Length: 0")

    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | sed '$d')

    if [ "$http_code" = "200" ] && echo "$body" | grep -q '"success":true'; then
        log_info "登录成功"
        return 0
    else
        log_error "登录失败 (HTTP ${http_code}): ${body}"
        return 1
    fi
}

# ==================== API 调用函数 ====================

# 添加流控规则
add_flow_rule() {
    local app="$1"
    local resource="$2"
    local count="$3"

    local json=$(cat <<EOF
{
    "app": "${app}",
    "resource": "${resource}",
    "limitApp": "default",
    "grade": 1,
    "count": ${count},
    "strategy": 0,
    "controlBehavior": 0,
    "clusterMode": false
}
EOF
)

    local response=$(curl -s -c "$COOKIE_FILE" -b "$COOKIE_FILE" -w "\n%{http_code}" \
        -X POST "${DASHBOARD_URL}/v2/flow/rule" \
        -H "Content-Type: application/json" \
        -d "$json")

    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | sed '$d')

    if [ "$http_code" = "200" ] || [ "$http_code" = "201" ]; then
        log_info "流控规则添加成功: ${app}/${resource} (QPS=${count})"
    else
        log_warn "流控规则添加失败: ${app}/${resource} (HTTP ${http_code}): ${body}"
    fi
}

# 获取服务的机器信息 (ip 和 port)
get_machine_info() {
    local app="$1"
    local response=$(curl -s -b "$COOKIE_FILE" "${DASHBOARD_URL}/v2/flow/rules?app=${app}")
    # 提取 ip 和 port
    local ip=$(echo "$response" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data'][0]['ip'] if d.get('data') else '')" 2>/dev/null)
    local port=$(echo "$response" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data'][0]['port'] if d.get('data') else '')" 2>/dev/null)
    echo "${ip}:${port}"
}

# 添加熔断规则
add_degrade_rule() {
    local app="$1"
    local resource="$2"
    local count="${3:-0.5}"
    local time_window="${4:-10}"
    local machine_info="$5"

    # 如果没有传入 machine_info，尝试获取
    if [ -z "$machine_info" ] || [ "$machine_info" = ":" ]; then
        machine_info=$(get_machine_info "$app")
    fi

    local ip=$(echo "$machine_info" | cut -d: -f1)
    local port=$(echo "$machine_info" | cut -d: -f2)

    # 构建 JSON - 如果有 ip 和 port 则包含，否则不包含
    local json
    if [ -n "$ip" ] && [ -n "$port" ]; then
        json=$(cat <<EOF
{
    "app": "${app}",
    "ip": "${ip}",
    "port": ${port},
    "resource": "${resource}",
    "limitApp": "default",
    "grade": 1,
    "count": ${count},
    "timeWindow": ${time_window},
    "minRequestAmount": 5,
    "statIntervalMs": 1000,
    "slowRatioThreshold": 0.5
}
EOF
)
    else
        # 没有机器信息时，只添加规则到 Dashboard（不推送到服务）
        json=$(cat <<EOF
{
    "app": "${app}",
    "resource": "${resource}",
    "limitApp": "default",
    "grade": 1,
    "count": ${count},
    "timeWindow": ${time_window},
    "minRequestAmount": 5,
    "statIntervalMs": 1000,
    "slowRatioThreshold": 0.5
}
EOF
)
    fi

    local response=$(curl -s -c "$COOKIE_FILE" -b "$COOKIE_FILE" -w "\n%{http_code}" \
        -X POST "${DASHBOARD_URL}/degrade/rule" \
        -H "Content-Type: application/json" \
        -d "$json")

    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | sed '$d')

    if [ "$http_code" = "200" ] || [ "$http_code" = "201" ]; then
        log_info "熔断规则添加成功: ${app}/${resource}"
    else
        log_warn "熔断规则添加失败: ${app}/${resource} (HTTP ${http_code}): ${body}"
    fi
}

# 添加热点参数限流规则
add_param_rule() {
    local app="$1"
    local resource="$2"
    local count="$3"
    local param_idx="${4:-0}"
    local machine_info="$5"

    # 如果没有传入 machine_info，尝试获取
    if [ -z "$machine_info" ] || [ "$machine_info" = ":" ]; then
        machine_info=$(get_machine_info "$app")
    fi

    local ip=$(echo "$machine_info" | cut -d: -f1)
    local port=$(echo "$machine_info" | cut -d: -f2)

    # 构建 JSON - 如果有 ip 和 port 则包含，否则不包含
    local json
    if [ -n "$ip" ] && [ -n "$port" ]; then
        json=$(cat <<EOF
{
    "app": "${app}",
    "ip": "${ip}",
    "port": ${port},
    "resource": "${resource}",
    "limitApp": "default",
    "grade": 1,
    "paramIdx": ${param_idx},
    "count": ${count},
    "durationInSec": 1,
    "paramFlowItemList": []
}
EOF
)
    else
        # 没有机器信息时，只添加规则到 Dashboard
        json=$(cat <<EOF
{
    "app": "${app}",
    "resource": "${resource}",
    "limitApp": "default",
    "grade": 1,
    "paramIdx": ${param_idx},
    "count": ${count},
    "durationInSec": 1,
    "paramFlowItemList": []
}
EOF
)
    fi

    local response=$(curl -s -c "$COOKIE_FILE" -b "$COOKIE_FILE" -w "\n%{http_code}" \
        -X POST "${DASHBOARD_URL}/paramFlow/rule" \
        -H "Content-Type: application/json" \
        -d "$json")

    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | sed '$d')

    if [ "$http_code" = "200" ] || [ "$http_code" = "201" ]; then
        log_info "热点参数规则添加成功: ${app}/${resource} (count=${count})"
    else
        log_warn "热点参数规则添加失败: ${app}/${resource} (HTTP ${http_code}): ${body}"
    fi
}

# 添加网关流控规则
add_gateway_rule() {
    local app="$1"
    local resource="$2"
    local count="$3"

    local json=$(cat <<EOF
{
    "app": "${app}",
    "resource": "${resource}",
    "limitApp": "default",
    "grade": 1,
    "count": ${count},
    "strategy": 0,
    "controlBehavior": 0
}
EOF
)

    local response=$(curl -s -c "$COOKIE_FILE" -b "$COOKIE_FILE" -w "\n%{http_code}" \
        -X POST "${DASHBOARD_URL}/v2/flow/rule" \
        -H "Content-Type: application/json" \
        -d "$json")

    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | sed '$d')

    if [ "$http_code" = "200" ] || [ "$http_code" = "201" ]; then
        log_info "网关流控规则添加成功: ${app}/${resource} (QPS=${count})"
    else
        log_warn "网关流控规则添加失败: ${app}/${resource} (HTTP ${http_code}): ${body}"
    fi
}

# ==================== 服务配置函数 ====================

# 配置网关服务
configure_gateway_service() {
    log_info "========== 配置 ${GATEWAY_APP} =========="

    # 网关流控规则
    add_gateway_rule "$GATEWAY_APP" "/login" 500
    add_gateway_rule "$GATEWAY_APP" "/register" 100
    add_gateway_rule "$GATEWAY_APP" "/concerts" 1000
    add_gateway_rule "$GATEWAY_APP" "/concerts/{id}" 2000
    add_gateway_rule "$GATEWAY_APP" "/order/book" 5000
    add_gateway_rule "$GATEWAY_APP" "/order/{orderNo}" 2000
}

# 配置用户服务
configure_user_service() {
    log_info "========== 配置 ${USER_APP} =========="

    # URL 流控规则
    add_flow_rule "$USER_APP" "/login" 500
    add_flow_rule "$USER_APP" "/register" 100
    add_flow_rule "$USER_APP" "/me" 2000
    add_flow_rule "$USER_APP" "/profile" 500
    add_flow_rule "$USER_APP" "/password" 200
    add_flow_rule "$USER_APP" "/{id}" 2000
    add_flow_rule "$USER_APP" "/validate/{id}" 3000
}

# 配置演唱会服务
configure_ticket_service() {
    log_info "========== 配置 ${TICKET_APP} =========="

    # 热点参数限流
    add_param_rule "$TICKET_APP" "ConcertController:getConcertDetail" 20

    # Redis 熔断规则
    local redis_resources=(
        "redis:set" "redis:setWithTimeout" "redis:setEx" "redis:get"
        "redis:delete" "redis:hasKey" "redis:decrement" "redis:decrementBy"
        "redis:increment" "redis:incrementBy" "redis:setIfAbsent" "redis:setNx"
        "redis:getExpire" "redis:expire" "redis:executeLua" "redis:executeLuaGeneric"
        "redis:hSet" "redis:hGet" "redis:hGetAll" "redis:hMSet" "redis:hIncrBy" "redis:hExists"
    )
    for resource in "${redis_resources[@]}"; do
        add_degrade_rule "$TICKET_APP" "$resource"
    done

    # Feign 客户端熔断
    add_degrade_rule "$TICKET_APP" "GET:http://ticket-stock-service/stock/internal/batch/{concertId}"
    add_degrade_rule "$TICKET_APP" "POST:http://ticket-stock-service/stock/internal/init"
    add_degrade_rule "$TICKET_APP" "POST:http://ticket-stock-service/stock/internal/deleteByGradeIds"
    add_degrade_rule "$TICKET_APP" "POST:http://ticket-stock-service/stock/internal/update"
}

# 配置订单服务
configure_order_service() {
    log_info "========== 配置 ${ORDER_APP} =========="

    # 热点参数限流
    add_param_rule "$ORDER_APP" "OrderController:bookTicket" 10

    # Kafka Producer 流控
    add_flow_rule "$ORDER_APP" "kafka-producer:ticket-order-topic" 5000

    # Redis 熔断规则
    local redis_resources=(
        "redis:set" "redis:setWithTimeout" "redis:setEx" "redis:get"
        "redis:delete" "redis:hasKey" "redis:decrement" "redis:decrementBy"
        "redis:increment" "redis:incrementBy" "redis:setIfAbsent" "redis:setNx"
        "redis:getExpire" "redis:expire" "redis:executeLua" "redis:executeLuaGeneric"
        "redis:hSet" "redis:hGet" "redis:hGetAll" "redis:hMSet" "redis:hIncrBy" "redis:hExists"
    )
    for resource in "${redis_resources[@]}"; do
        add_degrade_rule "$ORDER_APP" "$resource"
    done

    # Feign 客户端熔断
    add_degrade_rule "$ORDER_APP" "GET:http://ticket-stock-service/stock/internal"
    add_degrade_rule "$ORDER_APP" "GET:http://ticket-service/ticket/internal/grades/{id}"
}

# 配置库存服务
configure_stock_service() {
    log_info "========== 配置 ${STOCK_APP} =========="

    # Redis 熔断规则
    local redis_resources=(
        "redis:set" "redis:setWithTimeout" "redis:setEx" "redis:get"
        "redis:delete" "redis:hasKey" "redis:decrement" "redis:decrementBy"
        "redis:increment" "redis:incrementBy" "redis:setIfAbsent" "redis:setNx"
        "redis:getExpire" "redis:expire" "redis:executeLua" "redis:executeLuaGeneric"
        "redis:hSet" "redis:hGet" "redis:hGetAll" "redis:hMSet" "redis:hIncrBy" "redis:hExists"
    )
    for resource in "${redis_resources[@]}"; do
        add_degrade_rule "$STOCK_APP" "$resource"
    done

    # Feign 客户端熔断
    add_degrade_rule "$STOCK_APP" "GET:http://ticket-service/ticket/internal/grades/{id}"
    add_degrade_rule "$STOCK_APP" "GET:http://ticket-order-service/order/internal/{orderNo}"
    add_degrade_rule "$STOCK_APP" "POST:http://ticket-order-service/order/internal"
    add_degrade_rule "$STOCK_APP" "PUT:http://ticket-order-service/order/internal/{orderNo}/fail"
    add_degrade_rule "$STOCK_APP" "PUT:http://ticket-order-service/order/internal/{orderNo}/paid"
    add_degrade_rule "$STOCK_APP" "GET:http://ticket-order-service/order/internal/check-bought"
    add_degrade_rule "$STOCK_APP" "GET:http://ticket-order-service/order/internal/count-purchased"
    add_degrade_rule "$STOCK_APP" "GET:http://ticket-user-service/api/users/validate/{id}"
}

# 导出所有规则
export_rules() {
    log_info "========== 导出规则配置 =========="

    mkdir -p "$OUTPUT_DIR"

    # 创建 JSON 输出
    echo "{" > "$OUTPUT_FILE"

    # 导出流控规则
    echo "  \"flowRules\": {" >> "$OUTPUT_FILE"
    local first=true
    for app in "$GATEWAY_APP" "$USER_APP" "$TICKET_APP" "$ORDER_APP" "$STOCK_APP"; do
        local rules=$(curl -s -c "$COOKIE_FILE" -b "$COOKIE_FILE" "${DASHBOARD_URL}/v2/flow/rules?app=${app}")
        if [ "$first" = true ]; then
            first=false
        else
            echo "," >> "$OUTPUT_FILE"
        fi
        echo "    \"${app}\": ${rules}" >> "$OUTPUT_FILE"
    done
    echo "  }," >> "$OUTPUT_FILE"

    # 导出熔断规则
    echo "  \"degradeRules\": {" >> "$OUTPUT_FILE"
    first=true
    for app in "$GATEWAY_APP" "$USER_APP" "$TICKET_APP" "$ORDER_APP" "$STOCK_APP"; do
        local rules=$(curl -s -c "$COOKIE_FILE" -b "$COOKIE_FILE" "${DASHBOARD_URL}/degrade/rules?app=${app}")
        if [ "$first" = true ]; then
            first=false
        else
            echo "," >> "$OUTPUT_FILE"
        fi
        echo "    \"${app}\": ${rules}" >> "$OUTPUT_FILE"
    done
    echo "  }," >> "$OUTPUT_FILE"

    # 导出热点参数规则
    echo "  \"paramRules\": {" >> "$OUTPUT_FILE"
    first=true
    for app in "$GATEWAY_APP" "$USER_APP" "$TICKET_APP" "$ORDER_APP" "$STOCK_APP"; do
        local rules=$(curl -s -c "$COOKIE_FILE" -b "$COOKIE_FILE" "${DASHBOARD_URL}/paramFlow/rules?app=${app}")
        if [ "$first" = true ]; then
            first=false
        else
            echo "," >> "$OUTPUT_FILE"
        fi
        echo "    \"${app}\": ${rules}" >> "$OUTPUT_FILE"
    done
    echo "  }," >> "$OUTPUT_FILE"

    # 导出网关流控规则
    echo "  \"gatewayRules\": {" >> "$OUTPUT_FILE"
    first=true
    for app in "$GATEWAY_APP"; do
        local rules=$(curl -s -c "$COOKIE_FILE" -b "$COOKIE_FILE" "${DASHBOARD_URL}/gateway/flow/list?app=${app}")
        if [ "$first" = true ]; then
            first=false
        else
            echo "," >> "$OUTPUT_FILE"
        fi
        echo "    \"${app}\": ${rules}" >> "$OUTPUT_FILE"
    done
    echo "  }" >> "$OUTPUT_FILE"

    echo "}" >> "$OUTPUT_FILE"

    log_info "规则已导出到: ${OUTPUT_FILE}"
}

# ==================== 主函数 ====================

main() {
    log_info "Sentinel Dashboard 规则初始化脚本"
    log_info "Dashboard URL: ${DASHBOARD_URL}"
    log_info "Username: ${DASHBOARD_USER}"
    echo ""

    # 登录
    if ! login_dashboard; then
        log_error "无法登录到 Sentinel Dashboard，请检查用户名密码"
        exit 1
    fi
    echo ""

    # 配置各服务规则
    configure_gateway_service
    echo ""
    configure_user_service
    echo ""
    configure_ticket_service
    echo ""
    configure_order_service
    echo ""
    configure_stock_service
    echo ""

    # 导出规则
    export_rules

    # 清理 cookie
    rm -f "$COOKIE_FILE"

    log_info "========== 完成 =========="
    log_info "请访问 Sentinel Dashboard 查看规则: ${DASHBOARD_URL}"
}

# 运行主函数
main "$@"
