# 后端服务依赖检查报告

**检查时间**: 2026-05-17\
**项目路径**: `/Users/sunguangxu/Documents/trae_projects/langchaindemo/backend`

***

## 一、依赖组件检查结果

### 1. Java 开发环境 ✅

| 项目      | 状态                           | 版本信息               | 路径                                                           |
| ------- | ---------------------------- | ------------------ | ------------------------------------------------------------ |
| **JDK** | ✅ 已安装                        | OpenJDK 21.0.6 LTS | `/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home` |
| **用途**  | 用于编译和运行 Spring Boot 3.2.5 应用 | <br />             | <br />                                                       |

**验证命令**:

```bash
java -version
# 输出: java version "21.0.6" 2025-01-21 LTS
```

***

### 2. Maven 构建工具 ✅

| 项目        | 状态                          | 版本信息               | 路径                                          |
| --------- | --------------------------- | ------------------ | ------------------------------------------- |
| **Maven** | ✅ 已安装                       | Apache Maven 3.6.3 | `/Users/sunguangxu/soft/apache-maven-3.6.3` |
| **用途**    | 项目构建、依赖管理和运行 Spring Boot 应用 | <br />             | <br />                                      |

**验证命令**:

```bash
mvn -version
# Apache Maven 3.6.3
# Java version: 21.0.6
```

***

### 3. PostgreSQL 数据库 ✅

| 项目             | 状态         | 版本信息                       | 路径                           |
| -------------- | ---------- | -------------------------- | ---------------------------- |
| **PostgreSQL** | ✅ 已安装      | PostgreSQL 18.4 (Homebrew) | `/opt/homebrew/bin/psql`     |
| **数据库工具**      | ✅ 已安装      | createdb 等命令行工具            | `/opt/homebrew/bin/createdb` |
| **服务状态**       | ⚠️ **未运行** | PostgreSQL 服务未启动           | -                            |

**验证命令**:

```bash
psql --version
# 输出: psql (PostgreSQL) 18.4 (Homebrew)

which createdb
# 输出: /opt/homebrew/bin/createdb

brew services list | grep postgresql
# 输出: postgresql@18     none
```

***

### 4. Redis 缓存服务 ✅

| 项目        | 状态        | 版本信息                 | 路径                               |
| --------- | --------- | -------------------- | -------------------------------- |
| **Redis** | ✅ 已安装     | Redis server v=8.6.3 | `/opt/homebrew/bin/redis-server` |
| **服务状态**  | ✅ **运行中** | Redis 服务已启动          | -                                |

**验证命令**:

```bash
redis-server --version
# 输出: Redis server v=8.6.3

brew services list | grep redis
# 输出: redis             started
```

***

## 二、环境准备指南

### 1. 启动 PostgreSQL 数据库 ⚠️

PostgreSQL 已安装但未运行，需要手动启动：

**启动命令**:

```bash
# 方式一：使用 brew services
brew services start postgresql@18

# 方式二：直接启动
pg_ctl -D /opt/homebrew/var/postgresql@18 start
```

**创建数据库**:

```bash
# 创建 example_db 数据库
createdb -U postgres example_db

# 或者如果配置了用户名密码
createdb -U admin example_db
```

**验证数据库**:

```bash
psql -U postgres -d example_db -c "SELECT version();"
```

***

### 2. Redis 服务 ✅

Redis 服务已经在运行，无需额外操作。

**验证 Redis 连接**:

```bash
# 测试 Redis 连接
redis-cli ping
# 预期输出: PONG
```

***

### 3. 配置文件调整

在启动应用前，需要根据实际环境修改 `application.yml`：

**文件路径**: `backend/src/main/resources/application.yml`

**需要修改的配置**:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/example_db
    username: postgres      # 根据实际用户名修改
    password: postgres      # 根据实际密码修改
  
  data:
    redis:
      host: localhost
      port: 6379
      password:            # 如果设置了密码，在这里填入
```

***

## 三、启动后端服务

### 完整启动流程:

```bash
# 1. 进入后端项目目录
cd /Users/sunguangxu/Documents/trae_projects/langchaindemo/backend

# 2. 启动 PostgreSQL（如果未运行）
brew services start postgresql@18

# 3. 创建数据库
createdb -U postgres example_db

# 4. 编译项目
mvn clean compile

# 5. 运行应用
mvn spring-boot:run
```

### 验证服务启动:

```bash
# 检查服务是否启动
curl http://localhost:8080/api-docs

# 访问 Swagger UI
open http://localhost:8080/swagger-ui.html
```

***

## 四、测试账号

应用启动后，系统会自动创建三个测试用户：

| 用户名         | 密码         | 角色     | 权限级别 |
| ----------- | ---------- | ------ | ---- |
| **admin**   | admin123   | 管理员    | 最高权限 |
| **kbadmin** | kbadmin123 | 知识库管理员 | 中等权限 |
| **user**    | user123    | 普通用户   | 基础权限 |

***

## 五、依赖版本兼容性说明

| 组件         | 最低要求 | 当前版本   | 兼容性    | 说明                            |
| ---------- | ---- | ------ | ------ | ----------------------------- |
| Java       | 17+  | 21.0.6 | ✅ 完全兼容 | Spring Boot 3.2.x 要求 Java 17+ |
| Maven      | 3.6+ | 3.6.3  | ✅ 完全兼容 | 所有功能正常                        |
| PostgreSQL | 13+  | 18.4   | ✅ 完全兼容 | 支持所有需要的功能                     |
| Redis      | 6+   | 8.6.3  | ✅ 完全兼容 | 支持所有缓存功能                      |

***

## 六、常见问题排查

### 问题 1: PostgreSQL 连接失败

**错误信息**:

```
Connection refused. Check that the hostname and port are correct
```

**解决方案**:

1. 确保 PostgreSQL 服务已启动
2. 检查数据库是否已创建
3. 验证用户名密码是否正确

### 问题 2: Redis 连接失败

**错误信息**:

```
Cannot connect to Redis server
```

**解决方案**:

```bash
# 启动 Redis
brew services start redis

# 或手动启动
redis-server

# 测试连接
redis-cli ping
```

### 问题 3: Maven 依赖下载失败

**解决方案**:

```bash
# 清理缓存重新下载
mvn clean install -U
```

***

## 七、总结

### ✅ 已满足的依赖

- Java 21.0.6 LTS
- Apache Maven 3.6.3
- PostgreSQL 18.4 (已安装)
- Redis 8.6.3 (运行中)

### ⚠️ 需要手动操作的项

- [ ] **启动 PostgreSQL 服务**
- [ ] **创建数据库** **`example_db`**
- [ ] **配置数据库连接信息**

### 🎉 所有依赖已准备就绪

完成上述手动操作后，即可成功启动后端服务！

***

**文档生成时间**: 2026-05-17\
**生成工具**: 自动检测脚本

<https://developer.aliyun.com/mirror/postgresql>
