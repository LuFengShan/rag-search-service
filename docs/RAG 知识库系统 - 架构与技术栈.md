# RAG 知识库系统 - 架构与技术栈

## 📋 概述

**RAG (Retrieval-Augmented Generation) 知识库智能问答系统**是一个企业级知识库问答平台，基于向量检索和生成式AI技术，为企业提供私有文档的智能问答能力。

---

## 🏗️ 系统架构

### 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            前端层 (Frontend)                             │
│                        (可扩展 - Web / Mobile)                          │
└─────────────────────────────┬───────────────────────────────────────────┘
                              │ HTTPS / REST API
┌─────────────────────────────▼───────────────────────────────────────────┐
│                        网关/负载均衡 (可选)                             │
│                        (Nginx / AWS ALB)                              │
└─────────────────────────────┬───────────────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────────────┐
│                        后端服务层 (Backend)                              │
│  ┌──────────────────────────────────────────────────────────────┐     │
│  │  Spring Boot 3.2.5 + Java 21                                │     │
│  ├──────────────────────────────────────────────────────────────┤     │
│  │  • API Gateway / REST Controllers                           │     │
│  │  • Service Layer (业务逻辑)                                  │     │
│  │  • Repository Layer (数据访问)                               │     │
│  └────────────────────────────┬─────────────────────────────────┘     │
└───────────────────────────────┼─────────────────────────────────────────┘
                              │
         ┌────────────────────┼────────────────────┐
         │                    │                    │
    ┌────▼────┐         ┌─────▼─────┐       ┌────▼─────┐
    │ Redis   │         │ PostgreSQL │       │  文件存储  │
    │ (缓存)   │         │ + pgvector │       │ (本地/OSS) │
    └─────────┘         └───────────┘       └──────────┘
                       (向量数据库)
```

---

## 📦 技术栈详解

### 1. 开发语言与框架

| 技术 | 版本 | 说明 | 配置位置 |
|------|------|------|---------|
| **Java** | 21 | 编程语言，现代特性支持 | [pom.xml#L21](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/pom.xml#L21) |
| **Spring Boot** | 3.2.5 | 应用开发框架，自动配置 | [pom.xml#L9-L12](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/pom.xml#L9-L12) |
| **Maven** | 3.6+ | 构建和依赖管理工具 | - |

---

### 2. Web & API 层

#### Spring Web 组件

| 技术 | 版本 | 说明 | 相关文件 |
|------|------|------|---------|
| **Spring Web MVC** | 3.2.5 | RESTful API 框架 | [pom.xml#L40-L43](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/pom.xml#L40-L43) |
| **Spring Security** | 6.x | 安全认证与授权 | [SecurityConfig.java](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/src/main/java/com/example/rag/config/SecurityConfig.java) |
| **SpringDoc OpenAPI** | 2.3.0 | API 文档生成（Swagger） | [pom.xml#L86-L90](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/pom.xml#L86-L90) |

#### API 端点结构

```
/api
├── /auth              # 认证接口
│   ├── /login        # 用户登录
│   └── /register     # 用户注册
│
├── /documents         # 文档管理
│   ├── /upload       # 上传文档
│   ├── /             # 分页查询
│   ├── /{id}         # 获取文档详情
│   └── /{id} (DELETE)# 删除文档
│
├── /knowledge         # 知识库管理
│   ├── /             # CRUD操作
│   ├── /{id}         # 知识库详情
│   └── /{id}/documents # 知识库文档列表
│
├── /qa               # 问答接口
│   ├── /ask          # 提问问题
│   └── /history      # 问答历史
│
└── /analytics        # 运营分析
    ├── /overview     # 数据概览
    └── /trends       # 趋势统计
```

---

### 3. 数据持久化层

#### 关系型数据库

| 技术 | 版本 | 说明 | 配置位置 |
|------|------|------|---------|
| **PostgreSQL** | 15+ | 主要关系型数据库 | [application.yml#L6-L9](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/src/main/resources/application.yml#L6-L9) |
| **Hibernate ORM** | 6.x | JPA 实现，对象映射 | [pom.xml#L45-L48](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/pom.xml#L45-L48) |
| **Spring Data JPA** | 3.2.x | 数据访问抽象层 | - |

#### 向量数据库扩展

| 技术 | 版本 | 说明 | 相关文件 |
|------|------|------|---------|
| **pgvector** | 0.5+ | PostgreSQL 向量扩展 | [DocumentChunk.java#L34](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/src/main/java/com/example/rag/entity/DocumentChunk.java#L34) |

**向量存储**：
```sql
-- 向量字段定义
@Column(columnDefinition = "vector(1536)")
private String embedding;  -- 1536维向量（对应 OpenAI text-embedding-ada-002）
```

**支持的检索方式**：
- 余弦相似度 (Cosine Similarity)
- L2 距离 (Euclidean Distance)
- 内积 (Inner Product)

#### 缓存层

| 技术 | 版本 | 说明 | 配置位置 |
|------|------|------|---------|
| **Redis** | 6.x+ | 缓存和会话存储 | [application.yml#L21-L24](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/src/main/resources/application.yml#L21-L24) |
| **Spring Data Redis** | 3.2.x | Redis 数据访问 | [pom.xml#L55-L58](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/pom.xml#L55-L58) |

---

### 4. 安全与认证层

| 技术 | 版本 | 说明 | 相关文件 |
|------|------|------|---------|
| **JWT (jjwt)** | 0.12.5 | JSON Web Token 认证 | [pom.xml#L66-L84](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/pom.xml#L66-L84) |
| **BCrypt** | 内置 | 密码加密算法 | [SecurityConfig.java#L53-L55](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/src/main/java/com/example/rag/config/SecurityConfig.java#L53-L55) |
| **Spring Security** | 6.x | 认证与授权框架 | [SecurityConfig.java](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/src/main/java/com/example/rag/config/SecurityConfig.java) |

**认证流程**：
```
用户登录 → 验证用户名密码 → 生成 JWT Token → 返回 Token
后续请求 → 验证 JWT Token → 认证通过 → 执行业务
```

**用户角色**：
| 角色 | 权限 |
|------|------|
| ADMIN | 系统管理员，全部权限 |
| KNOWLEDGE_BASE_ADMIN | 知识库管理员，文档和知识库管理 |
| USER | 普通用户，问答功能 |

---

### 5. 文档解析与处理

| 技术 | 版本 | 说明 | 相关文件 |
|------|------|------|---------|
| **Apache Tika** | 3.3.0 | 文档解析库，支持多种格式 | [pom.xml#L118-L129](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/pom.xml#L118-L129) |
| **DocumentParserService** | - | 自定义文档解析服务 | [DocumentParserService.java](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/src/main/java/com/example/rag/service/DocumentParserService.java) |

**支持的文档格式**：
- **文本格式**：TXT, MD, CSV, LOG, JSON, XML, HTML
- **Office文档**：DOC, DOCX, XLS, XLSX, PPT, PPTX
- **PDF文档**：PDF
- **其他格式**：ODT, ODS, ODP, EPUB, EML, MSG

**文档处理流程**：
```
上传文件 → 格式检测 → Tika解析 → 文本提取 → 智能分块 → 向量化 → 存储到pgvector
```

**智能分块策略**：
1. 按段落分割（双换行符）
2. 按句子分割（句号/感叹号/问号）
3. 长度限制（50-500字符）
4. 保持语义完整性

---

### 6. 监控与可观测性

| 技术 | 版本 | 说明 | 相关文件 |
|------|------|------|---------|
| **Micrometer Tracing** | 1.12.x | 链路追踪抽象层 | [pom.xml#L108-L111](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/pom.xml#L108-L111) |
| **Brave** | - | Zipkin 兼容的链路追踪 | [pom.xml#L113-L116](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/pom.xml#L113-L116) |
| **Spring Actuator** | 3.2.x | 健康检查和监控端点 | [pom.xml#L35-L38](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/pom.xml#L35-L38) |
| **Logback** | - | 日志框架 | [logback-spring.xml](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/src/main/resources/logback-spring.xml) |

**监控端点**：
- `/actuator/health` - 健康检查
- `/actuator/info` - 应用信息
- `/actuator/metrics` - 指标数据

**链路追踪**：
```
请求 → TraceId 生成 → 全程传递 → 日志记录 → 可追踪问题
```

**日志格式**：
```
yyyy-MM-dd HH:mm:ss.SSS [traceId] [spanId] Level - Message
```

---

### 7. 开发工具与辅助库

| 技术 | 版本 | 说明 | 相关文件 |
|------|------|------|---------|
| **Lombok** | - | 简化 Java 代码，减少样板代码 | [pom.xml#L92-L96](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/pom.xml#L92-L96) |
| **Janino** | - | 日志条件表达式支持 | [pom.xml#L98-L101](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/pom.xml#L98-L101) |
| **Spring Boot Test** | 3.2.x | 单元测试和集成测试框架 | [pom.xml#L131-L135](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/pom.xml#L131-L135) |

---

## 📁 核心架构组件

### 分层架构

```
┌─────────────────────────────────────────────────────────────┐
│ Controller 层 (控制层)                                       │
│ - 处理 HTTP 请求和响应                                       │
│ - 参数验证                                                    │
│ - 调用 Service 层                                             │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│ Service 层 (业务逻辑层)                                      │
│ - 业务逻辑处理                                                │
│ - 事务管理                                                    │
│ - 调用 Repository 层                                          │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│ Repository 层 (数据访问层)                                    │
│ - 数据库 CRUD 操作                                           │
│ - JPA / Spring Data JPA                                     │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│ Database 层 (数据库层)                                        │
│ - PostgreSQL (关系数据)                                      │
│ - pgvector (向量数据)                                        │
│ - Redis (缓存)                                                │
└─────────────────────────────────────────────────────────────┘
```

### 核心模块说明

#### 1. 认证模块 (Auth Module)

**位置**：`com.example.rag.security`

**核心类**：
- `JwtTokenProvider` - JWT 令牌生成和验证
- `JwtAuthenticationFilter` - JWT 认证过滤器
- `CustomUserDetails` - 自定义用户详情
- `CustomUserDetailsService` - 用户详情服务

**流程**：
```
登录请求 → 验证凭证 → 生成 Token → 响应 Token
后续请求 → 验证 Token → 提取用户信息 → 执行业务
```

#### 2. 文档管理模块 (Document Module)

**位置**：`com.example.rag.service`

**核心类**：
- `DocumentService` - 文档上传和管理
- `DocumentParserService` - Tika 文档解析
- `VectorService` - 向量生成和检索

**核心流程**：
```
上传文档 → Tika解析 → 智能分块 → 向量化 → 存入pgvector
```

**相关实体**：
- `Document` - 文档元数据
- `DocumentChunk` - 文档块和向量

#### 3. 问答模块 (QA Module)

**位置**：`com.example.rag.service`

**核心类**：
- `QAService` - RAG 问答逻辑

**核心流程**：
```
用户提问 → 向量化 → 检索相似文档 → 生成回答 → 返回结果
```

#### 4. 知识库管理模块 (Knowledge Base Module)

**位置**：`com.example.rag.service`

**核心类**：
- `KnowledgeBaseService` - 知识库管理

---

## 🔧 配置参数

### 应用配置 ([application.yml](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/src/main/resources/application.yml))

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `server.port` | 8080 | 服务端口 |
| `spring.datasource.url` | jdbc:postgresql://localhost:5432/example_db | 数据库连接地址 |
| `spring.datasource.username` | postgres | 数据库用户名 |
| `spring.datasource.password` | 123456 | 数据库密码 |
| `spring.data.redis.host` | localhost | Redis 地址 |
| `spring.data.redis.port` | 6379 | Redis 端口 |
| `spring.servlet.multipart.max-file-size` | 50MB | 单个文件大小限制 |
| `spring.servlet.multipart.max-request-size` | 50MB | 请求大小限制 |
| `jwt.secret` | - | JWT 签名密钥（需修改） |
| `jwt.expiration` | 86400000 (24小时) | Token 过期时间（毫秒） |
| `springdoc.api-docs.path` | /api-docs | OpenAPI 文档路径 |
| `springdoc.swagger-ui.path` | /swagger-ui.html | Swagger UI 路径 |
| `logging.file.name` | ./logs/rag-app.log | 日志文件路径 |
| `logging.logback.rollingpolicy.max-file-size` | 10MB | 单个日志文件大小 |
| `logging.logback.rollingpolicy.max-history` | 30 | 日志保留天数 |
| `logging.logback.rollingpolicy.total-size-cap` | 1GB | 总日志大小限制 |

---

## 📊 核心数据模型

### 用户实体 (User)

```java
@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(unique = true, nullable = false)
    private String username;
    
    @Column(nullable = false)
    private String password;  // BCrypt 加密
    
    @Column(unique = true)
    private String email;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;
    
    // 创建时间、更新时间...
}

public enum Role {
    ADMIN,
    KNOWLEDGE_BASE_ADMIN,
    USER
}
```

### 文档实体 (Document)

```java
@Entity
@Table(name = "documents")
public class Document {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private String title;
    
    private String filePath;       // 文件存储路径
    private String fileType;       // 文件类型
    private Integer fileSize;      // 文件大小（字节）
    private String metadata;       // 元数据（JSON）
    
    private UUID knowledgeBaseId;  // 所属知识库ID
    private UUID uploadedBy;       // 上传用户ID
    
    @Enumerated(EnumType.STRING)
    private Status status;         // 文档状态
    
    // 时间戳...
}

public enum Status {
    UPLOADING,    // 上传中
    PROCESSING,   // 处理中
    INDEXED,      // 已索引
    FAILED        // 处理失败
}
```

### 文档块实体 (DocumentChunk)

```java
@Entity
@Table(name = "document_chunk")
public class DocumentChunk {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    private UUID documentId;      // 所属文档ID
    private Integer chunkIndex;   // 块索引
    
    @Column(columnDefinition = "text")
    private String content;       // 块内容
    
    @Column(columnDefinition = "vector(1536)")
    private String embedding;     // 1536维向量
    
    private LocalDateTime createdAt;
}
```

---

## 🎯 关键技术决策

### 1. 向量数据库选择：pgvector

**优点**：
- 与 PostgreSQL 原生集成，无需额外部署
- 支持多种相似度算法（余弦、L2、内积）
- 成熟稳定，社区活跃
- 与现有 SQL 查询良好集成

**向量维度**：1536
- 对应 OpenAI `text-embedding-ada-002` 模型
- 兼顾精度和性能

### 2. 文档解析选择：Apache Tika

**优点**：
- 支持 1000+ 文档格式
- 成熟稳定，业界标准
- 支持元数据提取

**版本**：3.3.0
- 最新稳定版
- 支持现代 Java 特性

### 3. 认证方案：JWT + Spring Security

**优点**：
- 无状态，易于扩展
- 跨服务支持
- 成熟的安全生态

### 4. 分层架构：经典分层

**Controller → Service → Repository**
- 职责清晰
- 易于测试
- 便于维护

---

## 🚀 性能与可扩展性

### 性能优化

1. **Redis 缓存**
   - 热点数据缓存
   - 会话管理
   - 减少数据库压力

2. **数据库优化**
   - 向量索引（IVFFlat / HNSW）
   - 查询优化
   - 连接池配置

3. **异步处理**
   - 文档处理异步化
   - 避免请求阻塞

### 水平扩展

```
负载均衡 (Nginx)
    ├── 实例 1 (Spring Boot) ─┐
    ├── 实例 2 (Spring Boot) ─┼───→ PostgreSQL (主从)
    └── 实例 3 (Spring Boot) ─┘    Redis (集群)
```

---

## 📚 相关文档索引

| 文档 | 路径 | 说明 |
|------|------|------|
| **项目结构** | [docs/MAVEN_PROJECT_STRUCTURE.md](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/docs/MAVEN_PROJECT_STRUCTURE.md) | Maven工程结构详解 |
| **文档处理流程** | [docs/DOCUMENT_PROCESSING_WORKFLOW.md](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/docs/DOCUMENT_PROCESSING_WORKFLOW.md) | 文档上传→分块→向量化流程 |
| **Apache Tika集成** | [docs/APACHE_TIKA_INTEGRATION.md](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/docs/APACHE_TIKA_INTEGRATION.md) | Tika使用说明 |
| **测试指南** | [docs/TESTING_GUIDE.md](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/docs/TESTING_GUIDE.md) | 如何运行测试 |
| **API测试用例** | [docs/API_TEST_CASES.md](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/docs/API_TEST_CASES.md) | Python3实现的API测试 |
| **部署文档** | [deployment/README.md](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/deployment/README.md) | 单机和Docker部署指南 |

---

## 🎓 快速开始

### 环境要求

- Java 21
- Maven 3.6+
- PostgreSQL 15+ with pgvector
- Redis 6+

### 运行项目

```bash
# 1. 克隆项目
cd langchaindemo/backend

# 2. 配置数据库
# 编辑 src/main/resources/application.yml

# 3. 构建项目
mvn clean package -DskipTests

# 4. 运行服务
mvn spring-boot:run
# 或者
java -jar target/rag-1.0.0.jar

# 5. 访问
# - API 文档: http://localhost:8080/swagger-ui.html
# - 健康检查: http://localhost:8080/actuator/health
```

---

## 📞 技术支持

如遇问题，请查看相关文档或提交 Issue。
