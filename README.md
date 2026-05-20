# RAG 知识库智能问答系统 - 后端服务

基于 Spring Boot 3.2.5 的 RAG（检索增强生成）知识库智能问答系统后端服务。

## 📋 技术栈

- **Java**: 21 LTS
- **框架**: Spring Boot 3.2.5
- **数据库**: PostgreSQL 15 + pgvector
- **缓存**: Redis 6+（可选）
- **认证**: JWT
- **构建工具**: Maven 3.6+

## ✨ 核心功能

### 🔐 用户认证
- JWT 令牌认证
- 用户注册与登录
- 角色权限管理

### 📚 知识库管理
- 文档上传与解析
- 文本分段处理
- 向量嵌入与存储
- 文档版本管理

### 🔍 智能问答
- 向量相似度检索
- RAG 检索增强生成
- 多文档关联问答
- 上下文理解

### 📊 系统管理
- 用户管理
- 文档管理
- 系统配置
- 日志与监控

## 🚀 快速开始

### 环境要求

| 组件 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 21+ | Java 开发环境 |
| Maven | 3.6+ | 项目构建工具 |
| PostgreSQL | 15+ | 关系型数据库 |
| pgvector | 0.5+ | 向量数据库扩展 |
| Redis | 6+ | 缓存服务（可选）|

### 1. 环境准备

```bash
# macOS
brew install openjdk@21 maven postgresql@15 pgvector redis

# Ubuntu/Debian
sudo apt install temurin-21-jdk maven postgresql postgresql-contrib redis-server

# CentOS/RHEL
sudo dnf install temurin-21-jdk maven postgresql-server redis
```

### 2. 数据库配置

```sql
-- 创建数据库
CREATE DATABASE example_db;

-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;
```

### 3. 配置应用

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/example_db
    username: postgres
    password: your-password
```

### 4. 构建运行

```bash
# 使用 Maven Wrapper（推荐）
./mvnw clean package -DskipTests
./mvnw spring-boot:run

# 或使用系统 Maven
mvn clean package -DskipTests
java -jar target/rag-1.0.0.jar
```

### 5. 访问服务

- **应用地址**: http://localhost:8080
- **API文档**: http://localhost:8080/swagger-ui.html
- **健康检查**: http://localhost:8080/actuator/health

## 🔧 项目结构

```
backend/
├── src/main/java/com/example/rag/
│   ├── RagApplication.java          # 启动类
│   ├── controller/                   # REST API 控制层
│   ├── service/                      # 业务逻辑层
│   ├── repository/                   # 数据访问层
│   ├── entity/                       # 数据库实体
│   ├── dto/                          # 数据传输对象
│   ├── config/                       # 配置类
│   ├── security/                     # 安全认证
│   └── exception/                    # 异常处理
├── src/main/resources/
│   ├── application.yml               # 应用配置
│   └── schema.sql                    # 数据库初始化
├── src/test/                         # 测试代码
├── pom.xml                           # Maven 依赖配置
├── mvnw                              # Maven Wrapper (Unix)
├── mvnw.cmd                          # Maven Wrapper (Windows)
└── Dockerfile                        # Docker 构建文件
```

## 🔐 API 接口

### 认证接口

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/auth/login` | 用户登录 |
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/refresh` | 刷新令牌 |

### 文档接口

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/documents` | 上传文档 |
| GET | `/api/documents` | 获取文档列表 |
| GET | `/api/documents/{id}` | 获取文档详情 |
| PUT | `/api/documents/{id}` | 更新文档 |
| DELETE | `/api/documents/{id}` | 删除文档 |

### 问答接口

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/qa/question` | 提问 |
| GET | `/api/qa/history` | 获取问答历史 |

## 🐳 Docker 运行

### 构建镜像

```bash
docker build -t rag-backend .
```

### 运行容器

```bash
docker run -d \
  --name rag-app \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/example_db \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=123456 \
  rag-backend
```

### 使用 Docker Compose

```bash
docker compose up -d
```

## 🧪 测试

### 运行单元测试

```bash
./mvnw test
```

### 运行集成测试

```bash
./mvnw verify
```

## 🔧 开发指南

### 代码规范

- 遵循 Google Java Style Guide
- 使用 Lombok 简化代码
- 异常处理统一
- 日志使用 SLF4J

### 开发环境

推荐使用 IntelliJ IDEA 或 VS Code：

1. 安装 Lombok 插件
2. 配置 Java 21 SDK
3. 安装 Docker 插件（可选）

## 📝 API 使用示例

### 登录

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### 提问

```bash
curl -X POST http://localhost:8080/api/qa/question \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"question":"什么是RAG？"}'
```

## 📊 配置说明

### 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `SPRING_DATASOURCE_URL` | 数据库连接URL | jdbc:postgresql://localhost:5432/example_db |
| `SPRING_DATASOURCE_USERNAME` | 数据库用户名 | postgres |
| `SPRING_DATASOURCE_PASSWORD` | 数据库密码 | 123456 |
| `SPRING_REDIS_HOST` | Redis 主机 | localhost |
| `SPRING_REDIS_PORT` | Redis 端口 | 6379 |
| `JWT_SECRET` | JWT 密钥 | 64位随机字符串 |
| `JWT_EXPIRATION` | JWT 过期时间 | 86400000 |

## 🛠️ 故障排查

### 常见问题

**Q: 数据库连接失败**
- 检查 PostgreSQL 服务是否启动
- 验证数据库用户名密码
- 确认数据库端口是否正确

**Q: pgvector 扩展未找到**
- 安装 pgvector 扩展
- 确认扩展已启用: `CREATE EXTENSION vector;`

**Q: 端口被占用**
- 查找占用进程: `lsof -i :8080`
- 杀掉进程: `kill -9 <PID>`

### 日志查看

```bash
# 查看应用日志
tail -f logs/rag-app.log

# 查看启动日志
cat logs/startup.log
```

## 📄 许可证

MIT License

## 📧 技术支持

如有问题，请提交 Issue 或联系开发团队。
