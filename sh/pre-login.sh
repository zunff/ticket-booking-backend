#!/bin/bash
#
# 预登录脚本 - 批量获取用户Token并缓存到文件
#
# 使用方法：
#   ./sh/pre-login.sh [用户数量] [网关地址]
#
# 示例：
#   ./sh/pre-login.sh 500 http://192.168.249.231:9000
#

USER_COUNT=${1:-500}
BASE_URL=${2:-"http://192.168.249.231:9000"}

# 获取脚本所在目录的上级目录（项目根目录）
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
OUTPUT_FILE="$PROJECT_ROOT/k6-scripts/.token-cache.json"

PASSWORD="123456"

echo "=========================================="
echo "  预登录脚本 - 批量获取用户Token"
echo "=========================================="
echo "网关地址: $BASE_URL"
echo "用户数量: $USER_COUNT"
echo "输出文件: $OUTPUT_FILE"
echo ""

# 输出数组格式（k6 SharedArray 要求数组）
echo "[" > "$OUTPUT_FILE"

SUCCESS_COUNT=0
FAIL_COUNT=0

for i in $(seq 1 $USER_COUNT); do
    # 生成用户名，格式: k6_test_001
    USERNAME=$(printf "k6_test_%03d" $i)

    # 发送登录请求
    RESPONSE=$(curl -s -X POST "$BASE_URL/api/users/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" \
        --connect-timeout 10 \
        --max-time 30)

    # 提取token和userId
    TOKEN=$(echo "$RESPONSE" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    if data.get('code') == 200 and data.get('data', {}).get('token'):
        print(data['data']['token'])
except:
    pass
" 2>/dev/null)

    USER_ID=$(echo "$RESPONSE" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    if data.get('code') == 200 and data.get('data', {}).get('user'):
        print(data['data']['user']['id'])
except:
    pass
" 2>/dev/null)

    if [ -n "$TOKEN" ] && [ -n "$USER_ID" ]; then
        # 写入数组格式
        if [ $i -eq $USER_COUNT ]; then
            echo "  {\"index\": $i, \"userId\": $USER_ID, \"token\": \"$TOKEN\"}" >> "$OUTPUT_FILE"
        else
            echo "  {\"index\": $i, \"userId\": $USER_ID, \"token\": \"$TOKEN\"}," >> "$OUTPUT_FILE"
        fi
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        printf "\r✓ 进度: %d/%d (成功: %d, 失败: %d)" $i $USER_COUNT $SUCCESS_COUNT $FAIL_COUNT
    else
        FAIL_COUNT=$((FAIL_COUNT + 1))
        printf "\r✗ 进度: %d/%d (成功: %d, 失败: %d) - %s 登录失败" $i $USER_COUNT $SUCCESS_COUNT $FAIL_COUNT "$USERNAME"
    fi

    # 添加短暂延迟，避免过快请求
    sleep 0.05
done

echo "]" >> "$OUTPUT_FILE"

echo ""
echo "=========================================="
echo "  完成！成功: $SUCCESS_COUNT, 失败: $FAIL_COUNT"
echo "  Token缓存已保存到: $OUTPUT_FILE"
echo "=========================================="
