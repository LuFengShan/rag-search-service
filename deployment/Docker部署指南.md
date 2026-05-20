# RAG知识库智能问答系统 - Docker部署指南

## 一、概述

本文档介绍如何使用Docker和Docker Compose快速部署RAG知识库智能问答系统。Docker部署方式具有以下优势：

- ✅ **快速部署**：一键启动所有依赖服务
- ✅ **环境一致**：开发、测试、生产环境保持一致
- ✅ **易于管理**：统一管理容器生命周期
- ✅ **资源隔离**：服务之间相互隔离
- ✅ **易于扩展**：可以轻松水平扩展

## 二、环境要求

### 2.1 硬件要求

- **CPU**: 2核以上（推荐4核）
- **内存**: 4GB以上可用内存（推荐8GB）
- **硬盘**: 20GB以上可用空间
- **操作系统**: macOS、Linux、Windows

### 2.2 软件依赖

| 组件 | 版本要求 | 说明 |
|------|---------|------|
| Docker | 20.10+ | 容器引擎 |
| Docker Compose | 2.0+ | 容器编排工具 |

### 2.3 安装Docker

**macOS:**
```bash
# 使用Homebrew安装
brew install --cask docker

# 或下载Docker Desktop
# https://www.docker.com/products/docker-desktop
```

**Linux (Ubuntu):**
```bash
# 更新apt包索引
sudo apt-get update

# 安装依赖包
sudo apt-get install -y ca-certificates curl gnupg lsb-release

# 添加Docker官方GPG密钥
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# 设置Docker仓库
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 安装Docker
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# 启动Docker服务
sudo systemctl start docker
sudo systemctl enable docker

# 将当前用户添加到docker组（避免每次使用sudo）
sudo usermod -aG docker $USER
newgrp docker
```

**Linux (CentOS):**
```bash
# 安装依赖包
sudo yum install -y yum-utils

# 添加Docker仓库
sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo

# 安装Docker
sudo yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# 启动Docker服务
sudo systemctl start docker
sudo systemctl enable docker

# 配置Docker开机自启
sudo systemctl enable containerd
```

**Windows:**
```bash
# 下载Docker Desktop
# https://www.docker.com/products/docker-desktop
```

### 2.4 验证Docker安装

```bash
# 检查Docker版本
docker --version

# 检查Docker Compose版本
docker compose version

# 运行测试容器
docker run hello-world
```

## 三、项目结构

Docker部署需要以下文件：

```
deployment/
├── docker-compose.yml          # Docker Compose配置文件
├── Dockerfile                  # 应用镜像构建文件
├── config/
│   └── application.yml         # 应用配置文件
├── .env                        # 环境变量文件
└── README.md                   # 部署文档
```

## 四、快速开始

### 4.1 方式一：使用预构建镜像（推荐）

```bash
# 进入部署目录
cd deployment

# 复制环境变量文件
cp .env.example .env

# 启动所有服务
docker compose up -d

# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f
```

### 4.2 方式二：从源码构建

```bash
# 进入部署目录
cd deployment

# 构建镜像并启动（首次运行需要构建，可能需要几分钟）
docker compose up -d --build

# 查看构建进度
docker compose logs -f rag-app
```

## 五、详细配置

### 5.1 环境变量配置

编辑 `.env` 文件：

```bash
# 应用配置
APP_NAME=rag-app
APP_VERSION=1.0.0

# 数据库配置
POSTGRES_DB=example_db
POSTGRES_USER=postgres
POSTGRES_PASSWORD=123456

# Redis配置（可选）
REDIS_PASSWORD=

# JVM配置
JAVA_OPTS=-Xms512m -Xmx1024m

# 时区配置
TZ=Asia/Shanghai
```

### 5.2 Docker Compose配置详解

```yaml
version: '3.8'

services:
  # RAG应用服务
  rag-app:
    build:
      context: ..
      dockerfile: deployment/Dockerfile
    container_name: rag-app
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      - SPRING_DATASOURCE_USERNAME=${POSTGRES_USER}
      - SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}
      - SPRING_DATA_REDIS_HOST=redis
      - SPRING_DATA_REDIS_PORT=6379
      - JWT_SECRET=${JWT_SECRET}
      - JAVA_OPTS=${JAVA_OPTS}
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_started
    networks:
      - rag-network
    volumes:
      - ./logs:/app/logs
      - app-data:/app/data
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

  # PostgreSQL数据库服务
  postgres:
    image: ankane/pgvector:v0.5.1
    container_name: rag-postgres
    environment:
      - POSTGRES_DB=${POSTGRES_DB}
      - POSTGRES_USER=${POSTGRES_USER}
      - POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    networks:
      - rag-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

  # Redis缓存服务（可选）
  redis:
    image: redis:7-alpine
    container_name: rag-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    networks:
      - rag-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

networks:
  rag-network:
    driver: bridge

volumes:
  postgres-data:
    driver: local
  redis-data:
    driver: local
  app-data:
    driver: local
```

### 5.3 应用配置文件

创建 `config/application-prod.yml`：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/example_db
    username: postgres
    password: 123456
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      connection-timeout: 20000
  
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  
  data:
    redis:
      host: redis
      port: 6379

jwt:
  secret: your-production-secret-key-change-this-in-production-64-chars
  expiration: 86400000

logging:
  level:
    com.example.rag: INFO
    org.springframework: INFO
  file:
    name: ./logs/rag-app.log
```

## 六、Dockerfile配置

创建 `deployment/Dockerfile`：

```dockerfile
# 使用多阶段构建优化镜像大小
# 阶段1: 构建阶段
FROM maven:3.9-eclipse-temurin-21 AS builder

# 设置工作目录
WORKDIR /app

# 复制pom.xml
COPY pom.xml .

# 下载依赖（利用Docker缓存层）
RUN mvn dependency:go-offline -B

# 复制源代码
COPY src ./src

# 构建应用
RUN mvn package -DskipTests -B

# 阶段2: 运行阶段
FROM eclipse-temurin:21-jre-alpine

# 安装curl（用于healthcheck）
RUN apk add --no-cache curl

# 创建非root用户
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# 设置工作目录
WORKDIR /app

# 从构建阶段复制jar文件
COPY --from=builder /app/target/*.jar app.jar

# 创建日志目录
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

# 切换到非root用户
USER appuser

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

## 七、服务管理

### 7.1 启动服务

```bash
# 前台启动（查看实时日志）
docker compose up

# 后台启动
docker compose up -d

# 带构建启动
docker compose up -d --build
```

### 7.2 停止服务

```bash
# 优雅停止（等待容器完成当前请求）
docker compose stop

# 强制停止
docker compose kill

# 删除容器（保留数据卷）
docker compose down

# 删除容器和数据卷（完全清理）
docker compose down -v
```

### 7.3 查看服务状态

```bash
# 查看运行状态
docker compose ps

# 查看资源使用
docker stats

# 查看日志
docker compose logs -f rag-app
docker compose logs -f postgres
docker compose logs -f redis
```

### 7.4 重启服务

```bash
# 重启单个服务
docker compose restart rag-app

# 重启所有服务
docker compose restart
```

### 7.5 扩容服务

```bash
# 扩展RAG应用实例数
docker compose up -d --scale rag-app=3

# 查看扩展后的状态
docker compose ps
```

## 八、数据管理

### 8.1 数据持久化

数据通过Docker卷（Volume）持久化：

```bash
# 查看卷列表
docker volume ls

# 查看卷详情
docker volume inspect deployment_postgres-data
```

### 8.2 备份数据

```bash
# 备份PostgreSQL数据
docker exec rag-postgres pg_dump -U postgres example_db > backup_$(date +%Y%m%d).sql

# 备份Redis数据
docker exec rag-redis redis-cli SAVE

# 复制备份文件
docker cp rag-postgres:/backup.sql ./backup.sql
```

### 8.3 恢复数据

```bash
# 恢复PostgreSQL数据
cat backup.sql | docker exec -i rag-postgres psql -U postgres example_db

# 或使用docker cp
docker cp backup.sql rag-postgres:/backup.sql
docker exec rag-postgres psql -U postgres example_db < backup.sql
```

### 8.4 清理数据

```bash
# 删除所有数据卷（谨慎操作！）
docker compose down -v

# 删除特定卷
docker volume rm deployment_postgres-data
docker volume rm deployment_redis-data
```

## 九、监控与日志

### 9.1 查看应用日志

```bash
# 查看实时日志
docker compose logs -f rag-app

# 查看最近100行日志
docker compose logs --tail 100 rag-app

# 查看错误日志
docker compose logs rag-app | grep ERROR

# 导出日志到文件
docker compose logs rag-app > app.log
```

### 9.2 查看数据库日志

```bash
# 查看PostgreSQL日志
docker compose logs postgres

# 进入PostgreSQL容器
docker exec -it rag-postgres psql -U postgres

# 查看数据库连接
docker exec rag-postgres psql -U postgres -c "SELECT * FROM pg_stat_activity;"
```

### 9.3 性能监控

```bash
# 查看容器资源使用
docker stats

# 查看CPU使用率
docker stats --no-stream rag-app

# 查看内存使用
docker stats --format "table {{.Name}}\t{{.MemUsage}}" rag-app
```

### 9.4 容器健康检查

```bash
# 检查容器健康状态
docker inspect --format='{{json .State.Health}}' rag-app | jq

# 查看健康检查日志
docker inspect --format='{{json .State.Health.Log}}' rag-app | jq
```

## 十、网络配置

### 10.1 端口映射

默认端口映射：

| 服务 | 容器端口 | 主机端口 | 说明 |
|------|---------|---------|------|
| rag-app | 8080 | 8080 | 应用服务 |
| postgres | 5432 | 5432 | PostgreSQL |
| redis | 6379 | 6379 | Redis（可选） |

### 10.2 自定义端口

编辑 `docker-compose.yml`：

```yaml
services:
  rag-app:
    ports:
      - "9090:8080"  # 主机9090 -> 容器8080
  
  postgres:
    ports:
      - "5433:5432"  # 主机5433 -> 容器5432
```

### 10.3 外部访问配置

如果需要从外部访问，需要配置防火墙：

```bash
# Ubuntu/Debian
sudo ufw allow 8080/tcp
sudo ufw allow 5432/tcp
sudo ufw allow 6379/tcp

# CentOS/RHEL
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --permanent --add-port=5432/tcp
sudo firewall-cmd --reload
```

## 十一、安全配置

### 11.1 修改默认密码

首次部署后，**必须**修改以下密码：

1. PostgreSQL密码
2. Redis密码（如果启用）
3. JWT密钥
4. 应用默认用户密码

### 11.2 使用环境变量

所有敏感配置应使用环境变量：

```bash
# 创建.env文件
cat > .env << 'EOF'
POSTGRES_PASSWORD=your-strong-password-here
JWT_SECRET=your-64-character-random-secret-key-here
REDIS_PASSWORD=your-redis-password
EOF

# 限制.env文件权限
chmod 600 .env
```

### 11.3 使用Docker Secrets（Swarm模式）

```yaml
services:
  rag-app:
    secrets:
      - db_password
      - jwt_secret

secrets:
  db_password:
    file: ./secrets/db_password.txt
  jwt_secret:
    file: ./secrets/jwt_secret.txt
```

## 十二、故障排查

### 12.1 应用启动失败

**问题：** `rag-app` 容器不断重启

**排查步骤：**
```bash
# 1. 查看容器日志
docker compose logs rag-app

# 2. 检查数据库连接
docker compose exec postgres pg_isready

# 3. 检查网络连通性
docker compose exec rag-app ping postgres

# 4. 查看详细错误
docker compose logs --tail 200 rag-app
```

**常见原因：**
1. 数据库未就绪（依赖服务未启动）
2. 数据库连接信息错误
3. 端口被占用

### 12.2 数据库连接失败

**问题：** 应用日志显示 `Connection refused`

**解决方案：**
```bash
# 1. 检查PostgreSQL容器状态
docker compose ps postgres

# 2. 查看PostgreSQL日志
docker compose logs postgres

# 3. 检查健康状态
docker compose exec postgres pg_isready -U postgres

# 4. 重启PostgreSQL
docker compose restart postgres

# 5. 等待健康检查通过后，重启应用
docker compose restart rag-app
```

### 12.3 性能问题

**问题：** 应用响应缓慢

**排查步骤：**
```bash
# 1. 查看资源使用
docker stats

# 2. 查看JVM状态
docker exec rag-app jstat -gc $(docker exec rag-app jcmd | grep jar | awk '{print $1}')

# 3. 查看线程状态
docker exec rag-app jstack $(docker exec rag-app jcmd | grep jar | awk '{print $1}')

# 4. 增加资源
# 编辑docker-compose.yml
docker compose up -d --scale rag-app=2
```

**优化建议：**
1. 增加JVM堆内存：`JAVA_OPTS=-Xmx2g`
2. 增加应用实例数
3. 优化数据库查询
4. 启用Redis缓存

### 12.4 数据丢失

**问题：** 重启后数据丢失

**原因：** 未正确配置数据持久化

**解决方案：**
```bash
# 1. 停止服务
docker compose down

# 2. 创建持久化卷（如果不存在）
docker volume create deployment_postgres-data

# 3. 重新启动
docker compose up -d

# 4. 验证数据持久化
docker volume inspect deployment_postgres-data
```

## 十三、生产环境部署

### 13.1 Nginx反向代理配置

创建 `nginx.conf`：

```nginx
upstream rag_backend {
    server rag-app:8080;
}

server {
    listen 80;
    server_name your-domain.com;

    client_max_body_size 50M;

    location / {
        proxy_pass http://rag_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # 超时配置
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    location /swagger-ui.html {
        proxy_pass http://rag_backend;
    }

    location /api-docs {
        proxy_pass http://rag_backend;
    }
}
```

### 13.2 HTTPS配置

使用Let's Encrypt获取免费SSL证书：

```bash
# 安装certbot
sudo apt install certbot python3-certbot-nginx

# 获取证书
sudo certbot --nginx -d your-domain.com
```

### 13.3 系统服务配置

创建systemd服务文件 `/etc/systemd/system/rag.service`：

```ini
[Unit]
Description=RAG Application Docker Compose
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/path/to/deployment
ExecStart=/usr/local/bin/docker compose up -d
ExecStop=/usr/local/bin/docker compose stop
TimeoutStartSec=0

[Install]
WantedBy=multi-user.target
```

启用服务：

```bash
sudo systemctl daemon-reload
sudo systemctl enable rag
sudo systemctl start rag
```

### 13.4 定期备份

创建备份脚本 `backup.sh`：

```bash
#!/bin/bash
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR=/backup/rag
mkdir -p $BACKUP_DIR

# 备份数据库
docker exec rag-postgres pg_dump -U postgres example_db > $BACKUP_DIR/db_$DATE.sql

# 备份Redis
docker exec rag-redis redis-cli SAVE

# 清理30天前的备份
find $BACKUP_DIR -name "*.sql" -mtime +30 -delete

echo "Backup completed: $DATE"
```

添加到crontab：

```bash
# 编辑crontab
crontab -e

# 添加备份任务（每天凌晨2点执行）
0 2 * * * /path/to/backup.sh >> /var/log/rag_backup.log 2>&1
```

## 十四、更新与升级

### 14.1 应用更新

```bash
# 1. 拉取最新代码
git pull origin main

# 2. 重新构建镜像
docker compose up -d --build

# 3. 验证更新
docker compose logs -f rag-app
```

### 14.2 数据库迁移

```bash
# 1. 备份数据
./backup.sh

# 2. 更新应用
docker compose up -d --build

# 3. 执行数据库迁移（如果有）
docker compose exec rag-app java -jar app.jar migrate
```

### 14.3 回滚版本

```bash
# 1. 查看镜像历史
docker images rag-app

# 2. 使用指定版本标签启动
docker tag rag-app:1.0.0 rag-app:latest
docker compose up -d

# 3. 或从备份恢复数据库
docker compose down
docker volume rm deployment_postgres-data
docker compose up -d
cat backup.sql | docker exec -i rag-postgres psql -U postgres
```

## 十五、扩展阅读

### 15.1 Docker Compose文档
- 官方文档：https://docs.docker.com/compose/

### 15.2 PostgreSQL + pgvector
- pgvector GitHub：https://github.com/pgvector/pgvector
- PostgreSQL文档：https://www.postgresql.org/docs/

### 15.3 Spring Boot Docker
- Spring Boot Docker：https://spring.io/guides/topics/spring-boot-docker/
