#!/bin/bash

# 测试参数
CONCURRENT_REQUESTS=50           # 并发请求总数
URL="http://localhost:8080/order/create"
LOGIN_URL="http://localhost:8080/login"

# 1. 登录获取 JWT Token
echo "正在登录获取 JWT Token..."
LOGIN_RESPONSE=$(curl -s -i -X POST -d "username=shell&password=1234" $LOGIN_URL)
JWT_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -i 'Set-Cookie: JWT_TOKEN' | sed -n 's/.*JWT_TOKEN=\([^;]*\);.*/\1/p')

if [ -z "$JWT_TOKEN" ]; then
    echo "错误：无法获取 JWT Token"
    exit 1
fi
echo "JWT Token 获取成功: ${JWT_TOKEN:0:20}..."

# 2. 创建临时目录存放结果
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

# 3. 定义发送请求的函数（后台运行）
send_request() {
    local id=$1
    local token=$2
    # 只获取 HTTP 状态码，不输出 body
    curl -s -o /dev/null -w "%{http_code}\n" -X POST \
        -H "Authorization: Bearer $token" \
        -d "productName=test&quantity=1" \
        $URL >> "$TEMP_DIR/$id.txt" &
}

# 4. 启动所有并发请求
echo "开始发送 $CONCURRENT_REQUESTS 个并发请求..."
for i in $(seq 1 $CONCURRENT_REQUESTS); do
    send_request $i $JWT_TOKEN
done
wait

# 5. 统计结果
echo ""
echo "========== 测试结果 =========="
total=0
limit=0
error=0
success=0
for i in $(seq 1 $CONCURRENT_REQUESTS); do
    code=$(cat "$TEMP_DIR/$i.txt")
    total=$((total+1))
    case $code in
        429) limit=$((limit+1)) ;;
        500) error=$((error+1)) ;;
        302|200) success=$((success+1)) ;;
        *) echo "未知状态码: $code (请求 $i)" ;;
    esac
done
echo "总请求数: $total"
echo "成功 (200/302): $success"
echo "限流 (429): $limit"
echo "服务错误 (500): $error"