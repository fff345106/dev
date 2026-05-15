#!/bin/bash
# ============================================================
# 中国传统纹样数字档案系统 - 全量压力测试脚本
# 工具: wrk | 线程: 4 | 连接数: 100 | 持续时间: 30s
# ============================================================

set -euo pipefail

BASE_URL="http://localhost:8080"
THREADS=4
CONNECTIONS=100
DURATION="30s"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Tokens
GUEST_TOKEN="${WRK_GUEST_TOKEN:-eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJndWVzdF9iNzJhMTc3NyIsInVzZXJJZCI6LTEsInJvbGUiOiJHVUVTVCIsImlhdCI6MTc3ODgzMDM3NCwiZXhwIjoxNzc4OTE2Nzc0fQ.NFSZ7EFcx_8e40Mbw4nIxZ2b2LfDxzVwVwdmLXxmK9lTUzwz9-8PDgrVRzDH3F9X}"
ADMIN_TOKEN="${WRK_ADMIN_TOKEN:-eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJhZG1pbiIsInVzZXJJZCI6NCwicm9sZSI6IlNVUEVSX0FETUlOIiwiaWF0IjoxNzc4ODMwNDAxLCJleHAiOjE3Nzg5MTY4MDF9.yQutWBxblzwnlTIN5GuoROMvdnKWPu-6MOVQc0jUO8HR5_iIMBpGVq0QWE7KKpez}"

# Test data
PATTERN_ID="${PATTERN_ID:-76}"
PATTERN_CODE="${PATTERN_CODE:-AN-BD-DE-CN-MG-260412-005}"

# Result storage
REPORT_FILE="$SCRIPT_DIR/stress-test-report.txt"
CSV_FILE="$SCRIPT_DIR/stress-test-results.csv"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# ============================================================
# Helper: run wrk and parse results
# ============================================================
run_wrk() {
    local name="$1"
    local url="$2"
    local token="${3:-}"
    local method="${4:-GET}"
    local body="${5:-}"

    echo -e "${CYAN}  Testing: ${name}${NC}"
    echo -e "  ${BLUE}${method} ${url}${NC}"

    local output
    if [[ "$method" == "POST" && -n "$body" ]]; then
        export WRK_TOKEN="$token"
        export WRK_BODY="$body"
        output=$(wrk -t"$THREADS" -c"$CONNECTIONS" -d"$DURATION" -s "$SCRIPT_DIR/post.lua" "$url" 2>&1) || true
    elif [[ -n "$token" ]]; then
        export WRK_TOKEN="$token"
        output=$(wrk -t"$THREADS" -c"$CONNECTIONS" -d"$DURATION" -s "$SCRIPT_DIR/auth.lua" "$url" 2>&1) || true
    else
        output=$(wrk -t"$THREADS" -c"$CONNECTIONS" -d"$DURATION" "$url" 2>&1) || true
    fi

    # Parse results
    local rps lat_avg lat_p99 errors total_requests
    rps=$(echo "$output" | grep "Requests/sec" | awk '{print $2}' || echo "N/A")
    lat_avg=$(echo "$output" | grep "Latency" | head -1 | awk '{print $2}' || echo "N/A")
    lat_p99=$(echo "$output" | grep "99%" | awk '{print $2}' || echo "N/A")
    errors=$(echo "$output" | grep "Socket errors" | head -1 || echo "none")
    total_requests=$(echo "$output" | grep "requests in" | awk '{print $1}' || echo "N/A")

    local http_errors
    http_errors=$(echo "$output" | grep "Non-2xx" | awk '{print $2}' || echo "0")

    # Determine status
    local status="${GREEN}PASS${NC}"
    if [[ "$errors" != "none" ]]; then
        status="${RED}FAIL${NC}"
    elif [[ "$http_errors" != "0" && "$http_errors" != "" ]]; then
        status="${YELLOW}WARN${NC}"
    fi

    echo -e "  ${status}  Requests: ${total_requests} | RPS: ${rps} | Avg Latency: ${lat_avg} | P99: ${lat_p99}"
    if [[ "$errors" != "none" ]]; then
        echo -e "  ${RED}  $errors${NC}"
    fi
    if [[ "$http_errors" != "0" && "$http_errors" != "" ]]; then
        echo -e "  ${YELLOW}  Non-2xx responses: ${http_errors}${NC}"
    fi
    echo ""

    # Append to CSV
    echo "\"${name}\",\"${method}\",\"${url}\",\"${total_requests}\",\"${rps}\",\"${lat_avg}\",\"${lat_p99}\",\"${http_errors}\",\"${errors}\"" >> "$CSV_FILE"

    # Append to report
    cat >> "$REPORT_FILE" << EOF
---
[$name]
  Endpoint: $method $url
  Total Requests: $total_requests
  Requests/sec:   $rps
  Avg Latency:    $lat_avg
  P99 Latency:    $lat_p99
  Non-2xx Errors: $http_errors
  Socket Errors:  $errors

EOF
}

# ============================================================
# Initialize report
# ============================================================
echo "" > "$REPORT_FILE"
echo "name,method,url,total_requests,rps,avg_latency,p99_latency,non_2xx,socket_errors" > "$CSV_FILE"

echo -e "${BLUE}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  中国传统纹样数字档案系统 - 全量压力测试                      ║${NC}"
echo -e "${BLUE}║  配置: ${THREADS}线程 / ${CONNECTIONS}并发连接 / 每端点 ${DURATION}                    ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

cat >> "$REPORT_FILE" << EOF
============================================================
  中国传统纹样数字档案系统 - 压力测试报告
  时间: $(date '+%Y-%m-%d %H:%M:%S')
  配置: ${THREADS} threads / ${CONNECTIONS} connections / ${DURATION} per endpoint
============================================================

EOF

# ============================================================
# Phase 1: Public Endpoints (No Auth)
# ============================================================
echo -e "${YELLOW}━━━ 阶段 1/5: 公开接口（无需认证）━━━${NC}"
echo "=== Phase 1: Public Endpoints ===" >> "$REPORT_FILE"

run_wrk "健康检查" "$BASE_URL/actuator/health"
run_wrk "公开纹样图片列表" "$BASE_URL/api/open/patterns/images"
run_wrk "公开活动列表" "$BASE_URL/api/open/events/"
run_wrk "公开藏品列表" "$BASE_URL/api/open/collectibles/"
run_wrk "公开纹样详情(按编码)" "$BASE_URL/api/open/patterns/${PATTERN_CODE}/detail"

# ============================================================
# Phase 2: Guest Read Endpoints
# ============================================================
echo -e "${YELLOW}━━━ 阶段 2/5: 游客读取接口 ━━━${NC}"
echo "=== Phase 2: Guest Read Endpoints ===" >> "$REPORT_FILE"

run_wrk "纹样列表(分页)" "$BASE_URL/api/patterns?page=0&size=20" "$GUEST_TOKEN"
run_wrk "纹样详情(ID)" "$BASE_URL/api/patterns/${PATTERN_ID}" "$GUEST_TOKEN"
run_wrk "按主类别查询" "$BASE_URL/api/patterns/category/AN?page=0&size=20" "$GUEST_TOKEN"
run_wrk "按风格查询" "$BASE_URL/api/patterns/style/DE?page=0&size=20" "$GUEST_TOKEN"
run_wrk "按地区查询" "$BASE_URL/api/patterns/region/CN?page=0&size=20" "$GUEST_TOKEN"
run_wrk "按时期查询" "$BASE_URL/api/patterns/period/MG?page=0&size=20" "$GUEST_TOKEN"
run_wrk "统计信息" "$BASE_URL/api/stats" "$GUEST_TOKEN"
run_wrk "纹样排行榜" "$BASE_URL/api/patterns/ranking" "$GUEST_TOKEN"

# ============================================================
# Phase 3: Admin Read Endpoints
# ============================================================
echo -e "${YELLOW}━━━ 阶段 3/5: 管理员读取接口 ━━━${NC}"
echo "=== Phase 3: Admin Read Endpoints ===" >> "$REPORT_FILE"

run_wrk "用户列表" "$BASE_URL/api/users" "$ADMIN_TOKEN"
run_wrk "待审核列表" "$BASE_URL/api/audit/pending" "$ADMIN_TOKEN"
run_wrk "所有审核记录" "$BASE_URL/api/audit" "$ADMIN_TOKEN"
run_wrk "我的提交记录" "$BASE_URL/api/audit/my" "$ADMIN_TOKEN"
run_wrk "审核详情(ID)" "$BASE_URL/api/audit/${PATTERN_ID}" "$ADMIN_TOKEN"
run_wrk "通知列表" "$BASE_URL/api/notifications" "$ADMIN_TOKEN"
run_wrk "文章列表" "$BASE_URL/api/articles" "$ADMIN_TOKEN"

# ============================================================
# Phase 4: Auth Endpoints (POST)
# ============================================================
echo -e "${YELLOW}━━━ 阶段 4/5: 认证接口（POST）━━━${NC}"
echo "=== Phase 4: Auth Endpoints ===" >> "$REPORT_FILE"

run_wrk "游客登录" "$BASE_URL/api/auth/guest-login" "" "POST" "{}"
run_wrk "管理员登录" "$BASE_URL/api/auth/login" "" "POST" '{"username":"admin","password":"admin123"}'

# ============================================================
# Phase 5: Write Endpoints (Admin POST/PUT)
# ============================================================
echo -e "${YELLOW}━━━ 阶段 5/5: 写入接口（POST/PUT）━━━${NC}"
echo "=== Phase 5: Write Endpoints ===" >> "$REPORT_FILE"

run_wrk "创建草稿" "$BASE_URL/api/drafts" "$ADMIN_TOKEN" "POST" \
    '{"description":"压力测试草稿","mainCategory":"AN","subCategory":"BD","style":"TR","region":"CN","period":"QG"}'

run_wrk "提交审核" "$BASE_URL/api/audit/submit" "$ADMIN_TOKEN" "POST" \
    '{"description":"压力测试纹样","mainCategory":"AN","subCategory":"BD","style":"TR","region":"CN","period":"QG","imageSourceType":"EXTERNAL","imageUrl":"https://example.com/test.png"}'

# ============================================================
# Summary
# ============================================================
echo "" >> "$REPORT_FILE"
echo "=== Summary ===" >> "$REPORT_FILE"
echo "Completed at: $(date '+%Y-%m-%d %H:%M:%S')" >> "$REPORT_FILE"

echo -e "${GREEN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  压力测试完成！                                              ║${NC}"
echo -e "${GREEN}║  报告: $REPORT_FILE                                          ║${NC}"
echo -e "${GREEN}║  CSV:  $CSV_FILE                                             ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════════╝${NC}"
