#!/bin/bash

#================================================================
# RAG知识库系统 - 恢复脚本
# 功能: 从备份恢复数据库和应用数据
#================================================================

set -e

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

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

# 显示帮助
show_help() {
    cat << EOF
用法: $0 <备份文件> [选项]

参数:
  <备份文件>    备份文件路径 (.tar.gz 或 .sql)

选项:
  --db-only       仅恢复数据库
  --config-only   仅恢复配置文件
  --force         强制恢复（覆盖现有数据）
  --dry-run       仅检查不执行恢复
  --help          显示帮助信息

示例:
  # 恢复所有数据
  $0 ./backups/rag_backup_20240517.tar.gz

  # 仅恢复数据库
  $0 ./backups/rag_backup_20240517.tar.gz --db-only

  # 预览恢复内容
  $0 ./backups/rag_backup_20240517.tar.gz --dry-run
EOF
}

# 检查备份文件
check_backup() {
    local backup_file="$1"
    
    if [[ ! -f "$backup_file" ]]; then
        log_error "备份文件不存在: $backup_file"
        return 1
    fi
    
    # 解压到临时目录检查
    local temp_dir=$(mktemp -d)
    trap "rm -rf $temp_dir" EXIT
    
    tar -tzf "$backup_file" > /dev/null 2>&1
    
    if [[ $? -eq 0 ]]; then
        log_success "备份文件格式正确"
        
        if [[ "$DRY_RUN" == true ]]; then
            log_info "备份文件内容:"
            tar -tzf "$backup_file"
            return 1
        fi
        
        return 0
    else
        log_error "备份文件格式错误或已损坏"
        return 1
    fi
}

# 解压备份
extract_backup() {
    local backup_file="$1"
    local extract_dir="$2"
    
    log_info "解压备份文件..."
    tar -xzf "$backup_file" -C "$extract_dir"
    
    log_success "备份文件已解压"
}

# 恢复数据库
restore_database() {
    local backup_dir="$1"
    local db_name="${POSTGRES_DB:-example_db}"
    local db_user="${POSTGRES_USER:-postgres}"
    local db_host="${POSTGRES_HOST:-localhost}"
    local db_port="${POSTGRES_PORT:-5432}"
    
    local dump_file="$backup_dir/database.sql"
    
    if [[ ! -f "$dump_file" ]]; then
        log_warn "未找到数据库备份文件"
        return 0
    fi
    
    log_info "恢复数据库..."
    
    # 检查数据库连接
    if ! PGPASSWORD="${POSTGRES_PASSWORD:-123456}" psql -U "$db_user" -h "$db_host" -p "$db_port" -d "$db_name" -c "SELECT 1" &> /dev/null; then
        log_error "无法连接到数据库"
        return 1
    fi
    
    # 确认恢复
    if [[ "$FORCE" != true ]]; then
        echo ""
        log_warn "即将恢复数据库 $db_name"
        echo "所有现有数据将被覆盖！"
        read -p "确认恢复? (yes/no): " confirm
        if [[ "$confirm" != "yes" ]]; then
            log_info "取消恢复"
            return 1
        fi
    fi
    
    # 执行恢复
    PGPASSWORD="${POSTGRES_PASSWORD:-123456}" pg_restore \
        -U "$db_user" \
        -h "$db_host" \
        -p "$db_port" \
        -d "$db_name" \
        -c \
        --if-exists \
        "$dump_file"
    
    if [[ $? -eq 0 ]]; then
        log_success "数据库恢复成功"
        return 0
    else
        log_error "数据库恢复失败"
        return 1
    fi
}

# 恢复配置文件
restore_config() {
    local backup_dir="$1"
    local config_dir="$backup_dir/config"
    
    if [[ ! -d "$config_dir" ]]; then
        log_warn "未找到配置文件"
        return 0
    fi
    
    log_info "恢复配置文件..."
    
    # 恢复应用配置
    if [[ -f "$config_dir/application.yml" ]]; then
        cp "$config_dir/application.yml" ../backend/src/main/resources/application.yml
        log_success "应用配置已恢复"
    fi
    
    # 恢复环境变量（需要手动确认）
    if [[ -f "$config_dir/.env" ]]; then
        log_warn "环境变量文件需要手动恢复"
        echo "源文件: $config_dir/.env"
        echo "请根据需要手动复制"
    fi
}

# 恢复Redis
restore_redis() {
    local backup_dir="$1"
    local redis_file="$backup_dir/redis.rdb"
    
    if [[ ! -f "$redis_file" ]]; then
        log_warn "未找到Redis备份文件"
        return 0
    fi
    
    if ! command -v redis-cli &> /dev/null; then
        log_warn "Redis未安装，跳过"
        return 0
    fi
    
    log_info "恢复Redis数据..."
    log_warn "Redis恢复需要手动操作"
    echo "请执行以下步骤:"
    echo "1. 停止Redis服务"
    echo "2. 复制 $redis_file 到Redis数据目录"
    echo "3. 重启Redis服务"
}

# 主流程
main() {
    local backup_file=""
    local db_only=false
    local config_only=false
    DRY_RUN=false
    FORCE=false
    
    # 解析参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            --db-only)
                db_only=true
                shift
                ;;
            --config-only)
                config_only=true
                shift
                ;;
            --force)
                FORCE=true
                shift
                ;;
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            --help|-h)
                show_help
                exit 0
                ;;
            *)
                if [[ -z "$backup_file" ]]; then
                    backup_file="$1"
                fi
                shift
                ;;
        esac
    done
    
    if [[ -z "$backup_file" ]]; then
        log_error "请指定备份文件"
        show_help
        exit 1
    fi
    
    echo "========================================"
    echo "      RAG 知识库系统恢复工具"
    echo "========================================"
    echo ""
    
    # 检查备份
    check_backup "$backup_file" || exit 1
    
    # 创建临时目录
    local temp_dir=$(mktemp -d)
    trap "rm -rf $temp_dir" EXIT
    
    # 解压备份
    extract_backup "$backup_file" "$temp_dir"
    
    # 查找解压后的目录
    local backup_name=$(basename "$backup_file" .tar.gz)
    local backup_dir="$temp_dir/$backup_name"
    
    if [[ ! -d "$backup_dir" ]]; then
        backup_dir="$temp_dir"
    fi
    
    echo ""
    
    # 执行恢复
    if [[ "$config_only" == true ]]; then
        restore_config "$backup_dir"
    elif [[ "$db_only" == true ]]; then
        restore_database "$backup_dir"
    else
        restore_database "$backup_dir"
        restore_config "$backup_dir"
        restore_redis "$backup_dir"
    fi
    
    echo ""
    echo "========================================"
    echo "      恢复完成"
    echo "========================================"
    echo ""
    
    if [[ "$db_only" != true ]]; then
        log_info "请重启应用以加载新配置"
        echo "  cd ../backend && ./deploy.sh --restart"
    fi
}

main "$@"
