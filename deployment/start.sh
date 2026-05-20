#!/bin/bash

#================================================================
# RAG系统 - 启动方式选择脚本
# 自动检测系统环境并选择合适的启动方式
#================================================================

set -e

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

# 显示菜单
show_menu() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}  RAG 系统启动方式选择${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo ""
    echo "请选择启动方式:"
    echo "1) 直接运行 (./deploy.sh)"
    echo "2) Systemd 服务 (需要root权限)"
    echo "3) Supervisor 进程管理"
    echo "4) Docker 容器"
    echo "5) 查看当前状态"
    echo "6) 退出"
    echo ""
}

# 检查环境
check_environment() {
    log_info "检查运行环境..."
    
    # 检测操作系统
    if [[ "$OSTYPE" == "darwin"* ]]; then
        OS="macos"
        log_info "操作系统: macOS"
    elif [[ -f /etc/os-release ]]; then
        . /etc/os-release
        OS="$ID"
        log_info "操作系统: $NAME"
    else
        OS="unknown"
        log_info "操作系统: 未知"
    fi
    
    # 检测启动管理器
    if command -v systemctl &> /dev/null && systemctl --version &> /dev/null; then
        HAS_SYSTEMD=true
        log_info "Systemd: 可用"
    else
        HAS_SYSTEMD=false
        log_info "Systemd: 不可用"
    fi
    
    if command -v supervisorctl &> /dev/null; then
        HAS_SUPERVISOR=true
        log_info "Supervisor: 可用"
    else
        HAS_SUPERVISOR=false
        log_info "Supervisor: 不可用"
    fi
    
    if command -v docker &> /dev/null; then
        HAS_DOCKER=true
        log_info "Docker: 可用"
    else
        HAS_DOCKER=false
        log_info "Docker: 不可用"
    fi
    
    echo ""
}

# 直接运行
run_direct() {
    log_info "使用直接运行方式..."
    ./deploy.sh
}

# Systemd 安装
install_systemd() {
    if [[ $EUID -ne 0 ]]; then
        log_error "Systemd 安装需要 root 权限"
        log_info "请使用: sudo $0"
        return 1
    fi
    
    log_info "安装 Systemd 服务..."
    cd config/systemd
    ./install-service.sh
    cd ../..
}

# Docker 运行
run_docker() {
    log_info "使用 Docker 方式运行..."
    
    if [[ ! -d "deployment" ]]; then
        log_error "未找到 deployment 目录"
        return 1
    fi
    
    cd deployment
    
    if [[ ! -f ".env" ]]; then
        log_warn "未找到 .env 文件，创建默认配置..."
        cp .env.example .env
    fi
    
    log_info "启动 Docker 容器..."
    docker compose up -d
    
    log_success "Docker 容器已启动"
    log_info "查看日志: docker compose logs -f"
    log_info "停止服务: docker compose down"
    
    cd ..
}

# 查看状态
show_status() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}  RAG 系统状态${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo ""
    
    # 检查直接运行
    if pgrep -f "rag-1.0.0.jar" > /dev/null; then
        echo -e "${GREEN}● 直接运行: 运行中${NC}"
        pgrep -f "rag-1.0.0.jar" | xargs ps -p | tail -n1 | awk '{print "  PID:", $1}'
    else
        echo -e "${RED}● 直接运行: 未运行${NC}"
    fi
    
    # 检查 Systemd
    if systemctl is-active --quiet rag 2>/dev/null; then
        echo -e "${GREEN}● Systemd服务: 运行中${NC}"
    else
        echo -e "${RED}● Systemd服务: 未运行${NC}"
    fi
    
    # 检查 Docker
    if docker ps --format '{{.Names}}' | grep -q "rag-app"; then
        echo -e "${GREEN}● Docker容器: 运行中${NC}"
    else
        echo -e "${RED}● Docker容器: 未运行${NC}"
    fi
    
    # 检查端口
    if curl -sf http://localhost:8080/actuator/health &> /dev/null; then
        echo -e "${GREEN}● 应用健康检查: 通过${NC}"
    else
        echo -e "${RED}● 应用健康检查: 失败${NC}"
    fi
    
    echo ""
}

# 主流程
main() {
    clear
    echo -e "${BLUE}"
    echo "╔═══════════════════════════════════════════════════════════╗"
    echo "║     RAG 知识库智能问答系统 - 启动管理器              ║"
    echo "╚═══════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
    
    check_environment
    
    while true; do
        show_menu
        read -p "请选择 (1-6): " choice
        
        case $choice in
            1)
                run_direct
                ;;
            2)
                if [[ "$HAS_SYSTEMD" == true ]]; then
                    install_systemd
                else
                    log_error "Systemd 不可用"
                fi
                ;;
            3)
                if [[ "$HAS_SUPERVISOR" == true ]]; then
                    log_info "Supervisor 配置已准备好"
                    log_info "请手动配置: deployment/config/supervisord/rag.conf"
                else
                    log_error "Supervisor 不可用"
                fi
                ;;
            4)
                if [[ "$HAS_DOCKER" == true ]]; then
                    run_docker
                else
                    log_error "Docker 不可用"
                fi
                ;;
            5)
                show_status
                ;;
            6)
                echo "退出"
                exit 0
                ;;
            *)
                log_error "无效选择"
                ;;
        esac
        
        echo ""
        read -p "按 Enter 继续..."
    done
}

main "$@"
