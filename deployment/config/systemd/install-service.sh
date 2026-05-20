#!/bin/bash

#================================================================
# RAG系统 - Systemd 服务安装脚本
# 功能: 将RAG应用注册为系统服务
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

# 检查root权限
check_root() {
    if [[ $EUID -ne 0 ]]; then
        log_error "请使用 sudo 运行此脚本"
        exit 1
    fi
}

# 检查Systemd
check_systemd() {
    if ! command -v systemctl &> /dev/null; then
        log_error "此系统不支持 systemd"
        exit 1
    fi
    log_success "Systemd 已安装"
}

# 创建服务用户
create_user() {
    if id "rag" &>/dev/null; then
        log_info "用户 rag 已存在"
    else
        log_info "创建用户 rag..."
        useradd -r -s /bin/false -d /opt/rag rag
        log_success "用户创建完成"
    fi
}

# 创建目录
create_directories() {
    log_info "创建目录..."
    
    mkdir -p /opt/rag/backend
    mkdir -p /var/log/rag
    
    # 设置权限
    chown -R rag:rag /opt/rag
    chown -R rag:rag /var/log/rag
    
    log_success "目录创建完成"
}

# 复制文件
copy_files() {
    log_info "复制应用文件..."
    
    local script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    local project_dir="$(dirname "$script_dir")"
    
    # 复制JAR文件
    if [[ -f "$project_dir/backend/target/rag-1.0.0.jar" ]]; then
        cp "$project_dir/backend/target/rag-1.0.0.jar" /opt/rag/backend/
    else
        log_error "JAR文件不存在，请先构建项目"
        log_info "运行: cd backend && mvn clean package"
        exit 1
    fi
    
    # 复制配置文件
    if [[ -f "$project_dir/backend/src/main/resources/application.yml" ]]; then
        cp "$project_dir/backend/src/main/resources/application.yml" /opt/rag/backend/
    fi
    
    log_success "文件复制完成"
}

# 安装服务
install_service() {
    log_info "安装 systemd 服务..."
    
    local script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    
    # 复制服务文件
    cp "$script_dir/rag.service" /etc/systemd/system/
    
    # 设置权限
    chmod 644 /etc/systemd/system/rag.service
    
    # 重载systemd
    systemctl daemon-reload
    
    log_success "服务安装完成"
}

# 配置防火墙
configure_firewall() {
    log_info "配置防火墙..."
    
    # UFW
    if command -v ufw &> /dev/null; then
        ufw allow 8080/tcp
        ufw reload
        log_success "UFW 防火墙已配置"
    fi
    
    # Firewalld
    if command -v firewall-cmd &> /dev/null; then
        firewall-cmd --permanent --add-port=8080/tcp
        firewall-cmd --reload
        log_success "Firewalld 已配置"
    fi
    
    # iptables (备用)
    if command -v iptables &> /dev/null && ! command -v ufw &> /dev/null && ! command -v firewall-cmd &> /dev/null; then
        iptables -A INPUT -p tcp --dport 8080 -j ACCEPT
        log_success "iptables 已配置"
    fi
}

# 启用服务
enable_service() {
    log_info "启用服务..."
    
    systemctl enable rag
    systemctl start rag
    
    sleep 3
    
    if systemctl is-active --quiet rag; then
        log_success "服务启动成功"
    else
        log_error "服务启动失败"
        log_info "查看日志: journalctl -u rag -n 50"
        exit 1
    fi
}

# 主流程
main() {
    echo "========================================"
    echo "      RAG 系统服务安装"
    echo "========================================"
    echo ""
    
    check_root
    check_systemd
    create_user
    create_directories
    copy_files
    install_service
    configure_firewall
    enable_service
    
    echo ""
    echo "========================================"
    echo "      安装完成"
    echo "========================================"
    echo ""
    log_success "RAG 系统已安装为系统服务"
    echo ""
    echo "服务管理命令:"
    echo "  启动:   sudo systemctl start rag"
    echo "  停止:   sudo systemctl stop rag"
    echo "  重启:   sudo systemctl restart rag"
    echo "  状态:   sudo systemctl status rag"
    echo "  日志:   sudo journalctl -u rag -f"
    echo ""
    echo "访问地址: http://localhost:8080"
    echo ""
}

main "$@"
