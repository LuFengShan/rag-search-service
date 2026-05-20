# RAG 知识库智能问答系统 - 部署配置总览

## 📁 目录结构

```
deployment/                          # 部署配置统一目录
├── README.md                        # 本文档
├── deploy.sh                        # ⭐ 一键部署脚本
├── start.sh                         # 启动方式选择脚本
│
├── 一键部署脚本使用说明.md          # 部署脚本使用文档
├── 单机部署指南.md                   # 详细单机部署说明
├── Docker部署指南.md                 # Docker部署说明
│
├── docker-compose.yml               # Docker编排配置
├── Dockerfile                       # Docker镜像构建
├── .env                             # 环境变量配置
├── .env.example                     # 环境变量示例
│
├── config/                          # 配置文件目录
│   ├── application-prod.yml         # 生产环境配置
│   │
│   ├── systemd/                     # Systemd 服务配置
│   │   ├── rag.service              # Systemd 服务文件
│   │   └── install-service.sh       # Systemd 安装脚本
│   │
│   ├── nginx/                       # Nginx 反向代理配置
│   │   └── rag.conf                 # Nginx 配置文件
│   │
│   └── supervisord/                 # Supervisor 进程管理配置
│       └── rag.conf                 # Supervisor 配置文件
│
└── scripts/                         # 脚本工具目录
    ├── backup.sh                    # 数据备份脚本
    └── restore.sh                   # 数据恢复脚本
```

## 🚀 快速开始

### 一键部署（推荐）

```bash
cd deployment
./deploy.sh
```

### 启动方式选择

```bash
cd deployment
./start.sh
```

## 📋 部署方式对比

| 方式 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| **直接运行** | 简单，快速 | 需手动管理进程 | 开发、测试 |
| **Systemd** | 系统级管理，开机自启 | 需要root权限 | 生产环境 Linux |
| **Supervisor** | 进程监控，自动重启 | 需要额外安装 | 共享主机环境 |
| **Docker** | 环境隔离，易于部署 | 需要Docker知识 | 微服务架构 |

## 🛠️ 核心功能

### 1. 一键部署脚本 (`deploy.sh`)

自动执行以下步骤：
- ✅ 环境检测（JDK、Maven、PostgreSQL、Redis 版本检查）
- ✅ 数据库初始化（创建数据库、启用pgvector）
- ✅ Maven 打包
- ✅ 应用启动
- ✅ 健康检查

**使用示例：**
```bash
# 完整部署
./deploy.sh

# 仅检查环境
./deploy.sh --skip-build --skip-start

# 重启应用
./deploy.sh --restart

# 查看状态
./deploy.sh --status
```

### 2. 备份恢复脚本

**备份：**
```bash
./scripts/backup.sh
```

**恢复：**
```bash
./scripts/restore.sh <备份文件>
```

### 3. Systemd 服务配置

适用于生产环境 Linux 系统：

```bash
# 安装服务（需要root）
sudo ./config/systemd/install-service.sh

# 管理服务
sudo systemctl start rag       # 启动
sudo systemctl stop rag         # 停止
sudo systemctl restart rag      # 重启
sudo systemctl status rag       # 状态
sudo journalctl -u rag -f       # 日志
```

### 4. Nginx 反向代理

支持 HTTPS 和性能优化：

```bash
# 复制配置
sudo cp config/nginx/rag.conf /etc/nginx/sites-available/
sudo ln -s /etc/nginx/sites-available/rag.conf /etc/nginx/sites-enabled/

# 测试配置
sudo nginx -t

# 重载 Nginx
sudo systemctl reload nginx
```

## 🔧 配置说明

### 环境变量 (.env)

```bash
# 数据库配置
POSTGRES_DB=example_db
POSTGRES_USER=postgres
POSTGRES_PASSWORD=123456

# Redis配置
REDIS_PASSWORD=

# JWT配置
JWT_SECRET=your-64-char-secret-key

# JVM配置
JAVA_OPTS=-Xms512m -Xmx1024m

# 时区
TZ=Asia/Shanghai
```

### 应用配置 (application-prod.yml)

主要配置项：
- 数据库连接
- Redis连接
- JWT密钥和过期时间
- 日志级别和输出
- 文件上传限制

## 📊 监控和维护

### 查看日志

```bash
# 应用日志
tail -f logs/rag-app.log

# Systemd日志
journalctl -u rag -f

# Docker日志
docker compose logs -f
```

### 健康检查

```bash
# 直接访问
curl http://localhost:8080/actuator/health

# Docker环境
docker exec rag-app curl http://localhost:8080/actuator/health
```

### 数据库管理

```bash
# 连接数据库
psql -U postgres -d example_db

# 常用命令
\d                                   # 列出所有表
\dt                                  # 列出所有表
\du                                  # 列出所有用户
```

## 🔐 安全建议

### 1. 修改默认密码

- [ ] 修改 `POSTGRES_PASSWORD`
- [ ] 修改 `JWT_SECRET`
- [ ] 修改应用默认用户密码

### 2. 启用 HTTPS

1. 获取 SSL 证书
2. 配置 Nginx HTTPS
3. 强制 HTTP 重定向 HTTPS

### 3. 防火墙配置

```bash
# Ubuntu/Debian
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp

# CentOS/RHEL
sudo firewall-cmd --permanent --add-port=80/tcp
sudo firewall-cmd --permanent --add-port=443/tcp
sudo firewall-cmd --reload
```

## 🐛 故障排查

### 常见问题

**1. 端口被占用**
```bash
# 查找占用进程
lsof -i :8080

# 杀掉进程
kill -9 <PID>
```

**2. 数据库连接失败**
```bash
# 检查PostgreSQL状态
sudo systemctl status postgresql

# 重启PostgreSQL
sudo systemctl restart postgresql
```

**3. 内存不足**
```bash
# 增加JVM堆内存
# 编辑deploy.sh，调整-Xms和-Xmx参数
```

### 查看详细日志

```bash
# 部署日志
cat deployment/deploy.log

# 应用日志
cat deployment/logs/rag-app.log

# Systemd日志
journalctl -u rag -n 100
```

## 📚 相关文档

- [一键部署脚本使用说明](./一键部署脚本使用说明.md)
- [单机部署指南](./单机部署指南.md)
- [Docker部署指南](./Docker部署指南.md)
- [PostgreSQL + pgvector 官方文档](https://github.com/pgvector/pgvector)

## 🎯 快速参考

### 启动命令

```bash
# 直接运行
./deploy.sh

# Docker运行
docker compose up -d

# Systemd服务
sudo systemctl start rag
```

### 停止命令

```bash
# 直接运行
./deploy.sh --stop

# Docker停止
docker compose down

# Systemd停止
sudo systemctl stop rag
```

### 查看状态

```bash
# 部署脚本状态
./deploy.sh --status

# Docker状态
docker compose ps

# Systemd状态
sudo systemctl status rag
```

## 🔄 更新升级

### 应用更新

```bash
# 1. 备份数据
./scripts/backup.sh

# 2. 更新代码
git pull

# 3. 重新构建
./deploy.sh --skip-checks

# 4. 重启服务
./deploy.sh --restart
```

### 回滚版本

```bash
# 1. 停止服务
./deploy.sh --stop

# 2. 恢复数据库
./scripts/restore.sh backups/rag_backup_YYYYMMDD.tar.gz

# 3. 使用旧版本JAR
cp rag-1.0.0.jar.bak target/rag-1.0.0.jar

# 4. 重启服务
./deploy.sh
```

---

**文档版本:** v1.0.0  
**最后更新:** 2024-05-17  
**技术支持:** 如有问题请查看各部署文档或提交Issue
