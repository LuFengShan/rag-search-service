#!/bin/bash

#================================================================
# RAG知识库系统 - 备份脚本
# 功能: 备份数据库和应用数据
#================================================================

set -e

# 配置
BACKUP_DIR="${BACKUP_DIR:-./backups}"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_NAME="rag_backup_${DATE}"
TIMESTAMP=$(date +%Y-%m-%d\ %H:%M:%S)

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 创建备份目录
mkdir -p "$BACKUP_DIR/$BACKUP_NAME"

log_info "开始备份..."
log_info "备份目录: $BACKUP_DIR/$BACKUP_NAME"

# 备份数据库
backup_database() {
    log_info "备份 PostgreSQL 数据库..."
    
    local db_name="${POSTGRES_DB:-example_db}"
    local db_user="${POSTGRES_USER:-postgres}"
    local db_host="${POSTGRES_HOST:-localhost}"
    local db_port="${POSTGRES_PORT:-5432}"
    
    # 检查数据库连接
    if ! PGPASSWORD="${POSTGRES_PASSWORD:-123456}" psql -U "$db_user" -h "$db_host" -p "$db_port" -d "$db_name" -c "SELECT 1" &> /dev/null; then
        log_error "无法连接到数据库"
        return 1
    fi
    
    # 执行备份
    local dump_file="$BACKUP_DIR/$BACKUP_NAME/database.sql"
    
    PGPASSWORD="${POSTGRES_PASSWORD:-123456}" pg_dump \
        -U "$db_user" \
        -h "$db_host" \
        -p "$db_port" \
        -d "$db_name" \
        -F c \
        -b \
        -v \
        -f "$dump_file"
    
    if [[ $? -eq 0 ]]; then
        log_success "数据库备份成功: $dump_file"
        return 0
    else
        log_error "数据库备份失败"
        return 1
    fi
}

# 备份Redis（如果安装）
backup_redis() {
    log_info "备份 Redis 数据..."
    
    if ! command -v redis-cli &> /dev/null; then
        log_warn "Redis 未安装，跳过备份"
        return 0
    fi
    
    local redis_host="${REDIS_HOST:-localhost}"
    local redis_port="${REDIS_PORT:-6379}"
    
    # 检查Redis连接
    if ! redis-cli -h "$redis_host" -p "$redis_port" ping &> /dev/null; then
        log_warn "Redis 未运行，跳过备份"
        return 0
    fi
    
    # 执行备份
    local redis_file="$BACKUP_DIR/$BACKUP_NAME/redis.rdb"
    
    redis-cli -h "$redis_host" -p "$redis_port" SAVE
    
    # 复制RDB文件
    if [[ -f /var/lib/redis/dump.rdb ]]; then
        cp /var/lib/redis/dump.rdb "$redis_file"
    elif [[ -f ~/Library/Application\ Support/Redis/dump.rdb ]]; then
        cp ~/Library/Application\ Support/Redis/dump.rdb "$redis_file"
    fi
    
    log_success "Redis备份完成"
}

# 备份配置文件
backup_config() {
    log_info "备份配置文件..."
    
    local config_dir="$BACKUP_DIR/$BACKUP_NAME/config"
    mkdir -p "$config_dir"
    
    # 备份应用配置（排除敏感信息）
    if [[ -f ../backend/src/main/resources/application.yml ]]; then
        cp ../backend/src/main/resources/application.yml "$config_dir/application.yml"
        log_success "应用配置已备份"
    fi
    
    # 备份环境变量文件
    if [[ -f .env ]]; then
        # 脱敏处理
        cp .env "$config_dir/.env"
        sed -i.bak 's/password=.*/password=***MASKED***/g' "$config_dir/.env"
        rm -f "$config_dir/.env.bak"
        log_success "环境变量已备份（敏感信息已脱敏）"
    fi
}

# 创建备份信息文件
create_backup_info() {
    cat > "$BACKUP_DIR/$BACKUP_NAME/backup_info.txt" << EOF
备份时间: $TIMESTAMP
备份名称: $BACKUP_NAME
主机名: $(hostname)
操作系统: $(uname -s)

数据库信息:
  - 数据库名: ${POSTGRES_DB:-example_db}
  - 用户名: ${POSTGRES_USER:-postgres}
  - 主机: ${POSTGRES_HOST:-localhost}

包含文件:
  - database.sql: 数据库完整备份
  - redis.rdb: Redis数据（如果存在）
  - config/: 应用配置文件

注意事项:
  1. 恢复前请确保PostgreSQL服务正在运行
  2. 恢复数据库: pg_restore -U postgres -d example_db -c database.sql
  3. Redis恢复: 停止Redis，替换dump.rdb文件，重启Redis
EOF
}

# 清理旧备份
cleanup_old_backups() {
    local retention_days="${BACKUP_RETENTION_DAYS:-30}"
    
    log_info "清理 $retention_days 天前的备份..."
    
    find "$BACKUP_DIR" -type d -name "rag_backup_*" -mtime +$retention_days -exec rm -rf {} \; 2>/dev/null || true
    
    log_success "旧备份清理完成"
}

# 压缩备份
compress_backup() {
    log_info "压缩备份文件..."
    
    cd "$BACKUP_DIR"
    tar -czf "${BACKUP_NAME}.tar.gz" "$BACKUP_NAME"
    rm -rf "$BACKUP_NAME"
    
    log_success "备份已压缩: ${BACKUP_NAME}.tar.gz"
    log_info "备份文件大小: $(du -h "${BACKUP_NAME}.tar.gz" | cut -f1)"
}

# 主流程
main() {
    echo "========================================"
    echo "      RAG 知识库系统备份工具"
    echo "========================================"
    echo ""
    
    # 备份各个组件
    backup_database || log_warn "数据库备份失败，继续其他备份..."
    backup_redis
    backup_config
    create_backup_info
    cleanup_old_backups
    compress_backup
    
    echo ""
    echo "========================================"
    echo "      备份完成"
    echo "========================================"
    echo ""
    log_success "备份文件: $BACKUP_DIR/${BACKUP_NAME}.tar.gz"
    echo ""
}

# 显示使用帮助
show_help() {
    cat << EOF
用法: $0 [选项]

选项:
  --no-compress    不压缩备份文件
  --no-cleanup     不清理旧备份
  --help           显示帮助信息

环境变量:
  POSTGRES_DB         数据库名 (默认: example_db)
  POSTGRES_USER        数据库用户 (默认: postgres)
  POSTGRES_PASSWORD    数据库密码 (默认: 123456)
  POSTGRES_HOST        数据库主机 (默认: localhost)
  POSTGRES_PORT        数据库端口 (默认: 5432)
  REDIS_HOST          Redis主机 (默认: localhost)
  REDIS_PORT          Redis端口 (默认: 6379)
  BACKUP_DIR          备份目录 (默认: ./backups)
  BACKUP_RETENTION_DAYS 备份保留天数 (默认: 30)

示例:
  # 使用默认配置备份
  $0

  # 自定义备份目录
  BACKUP_DIR=/mnt/backup $0

  # 自定义数据库配置
  POSTGRES_DB=myapp POSTGRES_PASSWORD=secret $0
EOF
}

# 解析参数
NO_COMPRESS=false
NO_CLEANUP=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --no-compress)
            NO_COMPRESS=true
            shift
            ;;
        --no-cleanup)
            NO_CLEANUP=true
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

# 修改main函数以支持参数
main_with_params() {
    echo "========================================"
    echo "      RAG 知识库系统备份工具"
    echo "========================================"
    echo ""
    
    backup_database || log_warn "数据库备份失败，继续其他备份..."
    backup_redis
    backup_config
    create_backup_info
    
    if [[ "$NO_CLEANUP" != true ]]; then
        cleanup_old_backups
    fi
    
    if [[ "$NO_COMPRESS" != true ]]; then
        compress_backup
    else
        log_info "备份保存在: $BACKUP_DIR/$BACKUP_NAME"
    fi
    
    echo ""
    echo "========================================"
    echo "      备份完成"
    echo "========================================"
    echo ""
}

main_with_params
