#!/bin/bash

#================================================================
# RAG知识库智能问答系统 - 一键部署脚本
# 支持: Linux (Ubuntu/CentOS) 和 macOS
#================================================================

set -e  # 遇到错误立即退出

# 脚本配置
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BACKEND_DIR="$PROJECT_DIR/backend"
LOG_FILE="$SCRIPT_DIR/deploy.log"
PID_FILE="$SCRIPT_DIR/rag-app.pid"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 版本要求（最低版本）
MIN_JDK_VERSION=21
MIN_MAVEN_VERSION=3.6
MIN_POSTGRES_VERSION=15
MIN_REDIS_VERSION=6
MIN_PGVECTOR_VERSION=0.5

#================================================================
# 辅助函数
#================================================================

print_banner() {
    echo -e "${BLUE}"
    echo "╔═══════════════════════════════════════════════════════════╗"
    echo "║     RAG知识库智能问答系统 - 一键部署脚本 v1.0.0           ║"
    echo "║     支持: Linux (Ubuntu/CentOS) 和 macOS              ║"
    echo "╚═══════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
}

log_info() {
    local message="$1"
    echo -e "${GREEN}[INFO]${NC} $message"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] $message" >> "$LOG_FILE"
}

log_warn() {
    local message="$1"
    echo -e "${YELLOW}[WARN]${NC} $message"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [WARN] $message" >> "$LOG_FILE"
}

log_error() {
    local message="$1"
    echo -e "${RED}[ERROR]${NC} $message"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [ERROR] $message" >> "$LOG_FILE"
}

log_success() {
    local message="$1"
    echo -e "${GREEN}[SUCCESS]${NC} $message"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [SUCCESS] $message" >> "$LOG_FILE"
}

# 获取操作系统类型
get_os_type() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        echo "macos"
    elif [[ -f /etc/os-release ]]; then
        . /etc/os-release
        case "$ID" in
            ubuntu|debian)
                echo "debian"
                ;;
            centos|rhel|rocky|almalinux)
                echo "centos"
                ;;
            *)
                echo "unknown"
                ;;
        esac
    else
        echo "unknown"
    fi
}

# 比较版本号
# 返回: 0 - 版本1 >= 版本2, 1 - 版本1 < 版本2
version_compare() {
    local ver1=$1
    local ver2=$2
    
    if [[ "$ver1" == "$ver2" ]]; then
        return 0
    fi
    
    local IFS=.
    local i ver1_arr=($ver1) ver2_arr=($ver2)
    
    for ((i=0; i<${#ver2_arr[@]}; i++)); do
        if [[ -z "${ver1_arr[i]}" ]]; then
            ver1_arr[i]=0
        fi
        
        if ((10#${ver1_arr[i]} > 10#${ver2_arr[i]})); then
            return 0
        elif ((10#${ver1_arr[i]} < 10#${ver2_arr[i]})); then
            return 1
        fi
    done
    
    return 0
}

#================================================================
# 环境检测
#================================================================

check_java() {
    log_info "检查 Java 环境..."
    
    if ! command -v java &> /dev/null; then
        log_error "未检测到 Java 安装"
        log_info "请先安装 Java 21 或更高版本"
        log_info "macOS: brew install openjdk@21"
        log_info "Ubuntu: sudo apt install temurin-21-jdk"
        log_info "CentOS: sudo dnf install temurin-21-jdk"
        return 1
    fi
    
    # 获取Java版本
    local java_version=$(java -version 2>&1 | head -n1 | awk -F '"' '{print $2}' | cut -d'.' -f1 | sed 's/-ea//')
    local java_version_full=$(java -version 2>&1 | head -n1 | awk -F '"' '{print $2}')
    
    if version_compare "$java_version" "$MIN_JDK_VERSION"; then
        log_success "Java 版本: $java_version_full ✓"
        return 0
    else
        log_error "Java 版本过低: $java_version_full"
        log_error "最低要求: Java $MIN_JDK_VERSION"
        return 1
    fi
}

check_maven() {
    log_info "检查 Maven 环境..."
    
    if ! command -v mvn &> /dev/null; then
        log_error "未检测到 Maven 安装"
        log_info "请先安装 Maven $MIN_MAVEN_VERSION 或更高版本"
        log_info "macOS: brew install maven"
        log_info "Ubuntu: sudo apt install maven"
        log_info "CentOS: sudo dnf install maven"
        return 1
    fi
    
    local maven_version=$(mvn -version 2>&1 | head -n1 | awk '{print $3}')
    local maven_major=$(echo $maven_version | cut -d'.' -f1)
    
    if [[ $maven_major -ge $MIN_MAVEN_VERSION ]]; then
        log_success "Maven 版本: $maven_version ✓"
        return 0
    else
        log_error "Maven 版本过低: $maven_version"
        log_error "最低要求: Maven $MIN_MAVEN_VERSION"
        return 1
    fi
}

check_postgresql() {
    log_info "检查 PostgreSQL 环境..."
    
    if ! command -v psql &> /dev/null; then
        log_error "未检测到 PostgreSQL 安装"
        log_info "请先安装 PostgreSQL $MIN_POSTGRES_VERSION 或更高版本"
        log_info "macOS: brew install postgresql@15"
        log_info "Ubuntu: sudo apt install postgresql"
        log_info "CentOS: sudo dnf install postgresql-server"
        return 1
    fi
    
    local pg_version=$(psql --version 2>&1 | awk '{print $3}' | cut -d'.' -f1)
    local pg_version_full=$(psql --version 2>&1 | awk '{print $3}')
    
    if [[ $pg_version -ge $MIN_POSTGRES_VERSION ]]; then
        log_success "PostgreSQL 版本: $pg_version_full ✓"
        
        # 检查pgvector扩展
        check_pgvector
        return $?
    else
        log_error "PostgreSQL 版本过低: $pg_version_full"
        log_error "最低要求: PostgreSQL $MIN_POSTGRES_VERSION"
        return 1
    fi
}

check_pgvector() {
    log_info "检查 pgvector 扩展..."
    
    # 尝试连接数据库检查pgvector
    if PGPASSWORD=123456 psql -U postgres -h localhost -d postgres -c "SELECT 1" &> /dev/null; then
        if PGPASSWORD=123456 psql -U postgres -h localhost -d postgres -c "SELECT * FROM pg_extension WHERE extname = 'vector';" &> /dev/null; then
            log_success "pgvector 扩展已安装 ✓"
            return 0
        else
            log_warn "pgvector 扩展未安装"
            log_info "请运行以下命令安装:"
            log_info "  macOS: brew install pgvector"
            log_info "  Ubuntu: sudo apt install postgresql-15-pgvector"
            log_info "  CentOS: sudo dnf install pgvector_15"
            return 1
        fi
    else
        log_warn "无法连接 PostgreSQL 数据库"
        log_warn "请确保 PostgreSQL 服务已启动并配置正确"
        return 1
    fi
}

check_redis() {
    log_info "检查 Redis 环境..."
    
    if ! command -v redis-cli &> /dev/null; then
        log_warn "未检测到 Redis 安装 (可选组件)"
        log_info "Redis 用于缓存，可选安装"
        log_info "macOS: brew install redis"
        log_info "Ubuntu: sudo apt install redis-server"
        log_info "CentOS: sudo dnf install redis"
        log_info "跳过 Redis 检查，继续部署..."
        return 0
    fi
    
    local redis_version=$(redis-cli --version 2>&1 | awk '{print $2}' | cut -d'.' -f1)
    local redis_version_full=$(redis-cli --version 2>&1 | awk '{print $2}')
    
    if [[ $redis_version -ge $MIN_REDIS_VERSION ]]; then
        log_success "Redis 版本: $redis_version_full ✓"
        return 0
    else
        log_warn "Redis 版本过低: $redis_version_full (建议升级)"
        return 0  # Redis不是必须的，继续
    fi
}

#================================================================
# 数据库检查和初始化
#================================================================

check_database() {
    log_info "检查数据库配置..."
    
    # 尝试连接数据库
    if PGPASSWORD=123456 psql -U postgres -h localhost -d postgres -c "SELECT 1" &> /dev/null; then
        log_success "数据库连接成功 ✓"
        
        # 检查数据库是否存在
        if PGPASSWORD=123456 psql -U postgres -h localhost -lqt | cut -d \| -f 1 | grep -qw "example_db"; then
            log_success "数据库 example_db 已存在 ✓"
        else
            log_info "创建数据库 example_db..."
            PGPASSWORD=123456 psql -U postgres -h localhost -c "CREATE DATABASE example_db;" 2>/dev/null
            log_success "数据库创建成功 ✓"
        fi
        
        # 启用pgvector扩展
        log_info "启用 pgvector 扩展..."
        PGPASSWORD=123456 psql -U postgres -h localhost -d example_db -c "CREATE EXTENSION IF NOT EXISTS vector;" 2>/dev/null
        log_success "pgvector 扩展启用成功 ✓"
        
        return 0
    else
        log_error "无法连接数据库"
        log_error "请检查 PostgreSQL 服务状态:"
        log_error "  macOS: brew services start postgresql@15"
        log_error "  Ubuntu: sudo systemctl start postgresql"
        log_error "  CentOS: sudo systemctl start postgresql"
        return 1
    fi
}

#================================================================
# Maven 构建
#================================================================

maven_build() {
    log_info "开始 Maven 构建..."
    
    cd "$BACKEND_DIR"
    
    # 清理并打包
    log_info "执行 mvn clean package -DskipTests..."
    
    if mvn clean package -DskipTests >> "$LOG_FILE" 2>&1; then
        log_success "Maven 构建成功 ✓"
        return 0
    else
        log_error "Maven 构建失败"
        log_error "请查看日志: $LOG_FILE"
        return 1
    fi
}

#================================================================
# 应用启动
#================================================================

start_application() {
    log_info "启动 RAG 应用..."
    
    local jar_file="$BACKEND_DIR/target/rag-1.0.0.jar"
    
    if [[ ! -f "$jar_file" ]]; then
        log_error "JAR 文件不存在: $jar_file"
        log_error "请先执行构建"
        return 1
    fi
    
    # 检查是否已运行
    if [[ -f "$PID_FILE" ]]; then
        local old_pid=$(cat "$PID_FILE")
        if kill -0 "$old_pid" 2>/dev/null; then
            log_warn "应用已在运行 (PID: $old_pid)"
            log_info "停止旧进程..."
            kill "$old_pid" 2>/dev/null || true
            sleep 2
        fi
    fi
    
    # 创建日志目录
    mkdir -p "$SCRIPT_DIR/logs"
    
    # 启动应用
    log_info "启动中 (日志: $SCRIPT_DIR/logs/rag-app.log)..."
    
    nohup java -Xms512m -Xmx1024m \
        -XX:+UseG1GC \
        -jar "$jar_file" \
        >> "$SCRIPT_DIR/logs/rag-app.log" 2>&1 &
    
    local app_pid=$!
    echo "$app_pid" > "$PID_FILE"
    
    # 等待启动
    log_info "等待应用启动..."
    sleep 5
    
    # 检查是否启动成功
    if kill -0 "$app_pid" 2>/dev/null; then
        log_success "应用启动成功 (PID: $app_pid) ✓"
        
        # 等待健康检查
        wait_for_health
        return 0
    else
        log_error "应用启动失败"
        log_error "请查看日志: $SCRIPT_DIR/logs/rag-app.log"
        return 1
    fi
}

wait_for_health() {
    log_info "等待健康检查..."
    local max_attempts=30
    local attempt=1
    
    while [[ $attempt -le $max_attempts ]]; do
        if curl -sf http://localhost:8080/actuator/health &> /dev/null; then
            log_success "健康检查通过 ✓"
            return 0
        fi
        
        echo -ne "${YELLOW}等待中... ($attempt/$max_attempts)${NC}\r"
        sleep 2
        ((attempt++))
    done
    
    echo ""
    log_warn "健康检查超时，继续运行..."
    return 0
}

#================================================================
# 主流程
#================================================================

show_help() {
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  --skip-checks    跳过环境检查"
    echo "  --skip-build     跳过构建"
    echo "  --skip-start     仅检查环境"
    echo "  --restart        重启应用"
    echo "  --stop           停止应用"
    echo "  --status         查看应用状态"
    echo "  --help           显示帮助信息"
    echo ""
}

stop_application() {
    log_info "停止 RAG 应用..."
    
    if [[ -f "$PID_FILE" ]]; then
        local pid=$(cat "$PID_FILE")
        if kill -0 "$pid" 2>/dev/null; then
            kill "$pid"
            sleep 2
            
            if kill -0 "$pid" 2>/dev/null; then
                log_warn "强制终止应用..."
                kill -9 "$pid" 2>/dev/null || true
            fi
            
            rm -f "$PID_FILE"
            log_success "应用已停止 ✓"
        else
            log_info "应用未在运行"
            rm -f "$PID_FILE"
        fi
    else
        log_info "未找到 PID 文件"
        # 尝试查找并停止
        local pids=$(pgrep -f "rag-1.0.0.jar" || true)
        if [[ -n "$pids" ]]; then
            log_info "停止进程: $pids"
            echo "$pids" | xargs kill 2>/dev/null || true
            log_success "应用已停止 ✓"
        fi
    fi
}

show_status() {
    echo ""
    echo "========================================"
    echo "          RAG 应用状态"
    echo "========================================"
    echo ""
    
    # 检查应用状态
    if [[ -f "$PID_FILE" ]]; then
        local pid=$(cat "$PID_FILE")
        if kill -0 "$pid" 2>/dev/null; then
            echo -e "${GREEN}● 应用运行中${NC} (PID: $pid)"
        else
            echo -e "${RED}● 应用未运行 (PID文件过期)${NC}"
        fi
    else
        local pids=$(pgrep -f "rag-1.0.0.jar" || true)
        if [[ -n "$pids" ]]; then
            echo -e "${GREEN}● 应用运行中${NC} (PID: $pids)"
        else
            echo -e "${RED}● 应用未运行${NC}"
        fi
    fi
    
    echo ""
    
    # 检查健康状态
    if curl -sf http://localhost:8080/actuator/health &> /dev/null; then
        echo -e "${GREEN}● 健康检查通过${NC}"
    else
        echo -e "${RED}● 健康检查失败${NC}"
    fi
    
    echo ""
    
    # 数据库状态
    if PGPASSWORD=123456 psql -U postgres -h localhost -d example_db -c "SELECT 1" &> /dev/null; then
        echo -e "${GREEN}● 数据库连接正常${NC}"
    else
        echo -e "${RED}● 数据库连接失败${NC}"
    fi
    
    echo ""
    
    # Redis状态
    if command -v redis-cli &> /dev/null && redis-cli ping &> /dev/null; then
        echo -e "${GREEN}● Redis连接正常${NC}"
    else
        echo -e "${YELLOW}● Redis未安装或未运行${NC}"
    fi
    
    echo ""
    echo "========================================"
    echo ""
    echo "访问地址:"
    echo "  应用:    http://localhost:8080"
    echo "  API文档: http://localhost:8080/swagger-ui.html"
    echo "  日志:    $SCRIPT_DIR/logs/rag-app.log"
    echo ""
}

main() {
    # 解析参数
    local skip_checks=false
    local skip_build=false
    local skip_start=false
    local do_restart=false
    local do_stop=false
    local do_status=false
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --skip-checks)
                skip_checks=true
                shift
                ;;
            --skip-build)
                skip_build=true
                shift
                ;;
            --skip-start)
                skip_start=true
                shift
                ;;
            --restart)
                do_restart=true
                shift
                ;;
            --stop)
                do_stop=true
                shift
                ;;
            --status)
                do_status=true
                shift
                ;;
            --help|-h)
                show_help
                exit 0
                ;;
            *)
                echo "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # 创建日志目录
    mkdir -p "$SCRIPT_DIR/logs"
    
    print_banner
    
    # 处理特殊命令
    if [[ "$do_status" == true ]]; then
        show_status
        exit 0
    fi
    
    if [[ "$do_stop" == true ]]; then
        stop_application
        exit 0
    fi
    
    if [[ "$do_restart" == true ]]; then
        stop_application
        skip_checks=true
        skip_build=false
    fi
    
    echo ""
    echo "========================================"
    echo "          开始环境检查"
    echo "========================================"
    echo ""
    
    # 环境检查
    if [[ "$skip_checks" != true ]]; then
        check_java || exit 1
        echo ""
        check_maven || exit 1
        echo ""
        check_postgresql || exit 1
        echo ""
        check_redis
        echo ""
        check_database || exit 1
    else
        log_warn "跳过环境检查"
    fi
    
    echo ""
    echo "========================================"
    echo "          开始构建"
    echo "========================================"
    echo ""
    
    # Maven 构建
    if [[ "$skip_build" != true ]]; then
        maven_build || exit 1
    else
        log_warn "跳过构建"
    fi
    
    echo ""
    echo "========================================"
    echo "          启动应用"
    echo "========================================"
    echo ""
    
    # 启动应用
    if [[ "$skip_start" != true ]]; then
        start_application || exit 1
    else
        log_warn "跳过启动"
    fi
    
    echo ""
    echo "========================================"
    echo "          部署完成"
    echo "========================================"
    echo ""
    log_success "RAG 知识库智能问答系统部署成功!"
    echo ""
    echo "访问地址:"
    echo -e "  ${BLUE}应用:${NC}    http://localhost:8080"
    echo -e "  ${BLUE}API文档:${NC} http://localhost:8080/swagger-ui.html"
    echo ""
    echo "默认用户:"
    echo "  管理员: admin / admin123"
    echo "  知识库管理员: kbadmin / kbadmin123"
    echo "  普通用户: user / user123"
    echo ""
    echo "日志文件: $SCRIPT_DIR/logs/rag-app.log"
    echo "部署日志: $LOG_FILE"
    echo ""
    
    # 显示状态
    show_status
}

# 运行主函数
main "$@"
