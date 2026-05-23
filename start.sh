#!/bin/bash

# RAG Search Service 启动脚本

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 打印函数
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 显示用法
usage() {
    echo "用法: $0 [环境]"
    echo ""
    echo "环境选项:"
    echo "  dev     启动开发环境 (默认)"
    echo "  test    启动测试环境"
    echo "  prod    启动线上环境"
    echo ""
    echo "示例:"
    echo "  $0           # 启动开发环境"
    echo "  $0 dev       # 启动开发环境"
    echo "  $0 test      # 启动测试环境"
    echo "  $0 prod      # 启动线上环境"
}

# 检查环境
PROFILE=${1:-dev}

if [ "$PROFILE" != "dev" ] && [ "$PROFILE" != "test" ] && [ "$PROFILE" != "prod" ]; then
    print_error "无效的环境: $PROFILE"
    usage
    exit 1
fi

print_info "启动 RAG Search Service - 环境: $PROFILE"

# 检查 Java
if ! command -v java &> /dev/null; then
    print_error "Java 未安装或不在 PATH 中"
    exit 1
fi

# 检查 Maven
if ! command -v mvn &> /dev/null; then
    print_warn "Maven 未安装，尝试使用 mvnw"
    MVN_CMD="./mvnw"
else
    MVN_CMD="mvn"
fi

# 根据环境执行不同操作
case $PROFILE in
    dev)
        print_info "开发环境配置:"
        print_info "  - 数据库: PostgreSQL (localhost:5432)"
        print_info "  - Redis: localhost:6379"
        print_info "  - 日志级别: DEBUG"
        ;;
    test)
        print_info "测试环境配置:"
        print_info "  - 数据库: H2 内存数据库"
        print_info "  - Redis: localhost:6379"
        print_info "  - 日志级别: DEBUG"
        ;;
    prod)
        print_info "线上环境配置:"
        print_info "  - 数据库: PostgreSQL (通过环境变量配置)"
        print_info "  - Redis: 通过环境变量配置"
        print_info "  - 日志级别: INFO"

        # 检查必需的环境变量
        if [ -z "$DB_PASSWORD" ] || [ -z "$DEEPSEEK_API_KEY" ] || [ -z "$DASHSCOPE_API_KEY" ] || [ -z "$JWT_SECRET" ]; then
            print_error "线上环境需要配置以下环境变量:"
            echo "  - DB_PASSWORD"
            echo "  - DEEPSEEK_API_KEY"
            echo "  - DASHSCOPE_API_KEY"
            echo "  - JWT_SECRET"
            print_warn "请确保这些环境变量已设置，或使用 .env 文件加载"
            exit 1
        fi
        ;;
esac

# 启动应用
print_info "启动 Spring Boot 应用..."
$MVN_CMD spring-boot:run -Dspring-boot.run.profiles=$PROFILE
