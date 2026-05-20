# RAG 知识库系统 - Maven 工程结构

本文档详细介绍 RAG 知识库智能问答系统的完整 Maven 工程结构。

## 📁 目录结构

```
backend/                                    # 后端服务根目录
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/rag/
│   │   │       ├── RagApplication.java          # 启动类
│   │   │       │
│   │   │       ├── config/                      # 配置层
│   │   │       │   ├── SecurityConfig.java       # 安全配置
│   │   │       │   ├── CorsConfig.java          # CORS配置
│   │   │       │   └── TracingConfig.java        # 链路追踪配置
│   │   │       │
│   │   │       ├── controller/                  # 控制层（REST API）
│   │   │       │   ├── BaseController.java      # Controller基类
│   │   │       │   ├── AuthController.java      # 认证接口
│   │   │       │   ├── UserController.java      # 用户管理
│   │   │       │   ├── DocumentController.java  # 文档管理
│   │   │       │   ├── KnowledgeBaseController.java  # 知识库管理
│   │   │       │   ├── QAController.java        # 问答接口
│   │   │       │   └── AnalyticsController.java # 运营分析
│   │   │       │
│   │   │       ├── service/                     # 业务逻辑层
│   │   │       │   ├── UserService.java         # 用户服务
│   │   │       │   ├── DocumentService.java     # 文档服务
│   │   │       │   ├── DocumentParserService.java # 文档解析（Tika）
│   │   │       │   ├── VectorService.java       # 向量服务
│   │   │       │   ├── QAService.java           # 问答服务
│   │   │       │   └── KnowledgeBaseService.java # 知识库服务
│   │   │       │
│   │   │       ├── repository/                   # 数据访问层
│   │   │       │   ├── UserRepository.java       # 用户仓储
│   │   │       │   ├── DocumentRepository.java   # 文档仓储
│   │   │       │   ├── DocumentChunkRepository.java # 文档块仓储
│   │   │       │   ├── DocumentChunkVectorRepository.java # 向量仓储
│   │   │       │   ├── KnowledgeBaseRepository.java # 知识库仓储
│   │   │       │   ├── QuestionRepository.java   # 问题仓储
│   │   │       │   └── AnswerRepository.java     # 答案仓储
│   │   │       │
│   │   │       ├── entity/                       # 数据实体
│   │   │       │   ├── User.java                # 用户实体
│   │   │       │   ├── Document.java             # 文档实体
│   │   │       │   ├── DocumentChunk.java        # 文档块实体
│   │   │       │   ├── KnowledgeBase.java        # 知识库实体
│   │   │       │   ├── Question.java             # 问题实体
│   │   │       │   └── Answer.java               # 答案实体
│   │   │       │
│   │   │       ├── dto/                         # 数据传输对象
│   │   │       │   ├── request/                 # 请求DTO
│   │   │       │   │   ├── LoginRequest.java
│   │   │       │   │   ├── CreateUserRequest.java
│   │   │       │   │   ├── QuestionRequest.java
│   │   │       │   │   └── ...
│   │   │       │   │
│   │   │       │   └── response/                # 响应DTO
│   │   │       │       ├── ApiResponse.java
│   │   │       │       ├── LoginResponse.java
│   │   │       │       ├── UserResponse.java
│   │   │       │       └── ...
│   │   │       │
│   │   │       ├── security/                     # 安全认证
│   │   │       │   ├── JwtTokenProvider.java    # JWT令牌提供者
│   │   │       │   ├── JwtAuthenticationFilter.java # JWT过滤器
│   │   │       │   ├── CustomUserDetails.java   # 自定义用户详情
│   │   │       │   └── CustomUserDetailsService.java # 用户详情服务
│   │   │       │
│   │   │       ├── exception/                   # 异常处理
│   │   │       │   ├── ResourceNotFoundException.java
│   │   │       │   ├── BusinessException.java
│   │   │       │   └── GlobalExceptionHandler.java
│   │   │       │
│   │   │       └── filter/                      # 过滤器
│   │   │           ├── RequestLoggingFilter.java # 请求日志
│   │   │           └── ...
│   │   │
│   │   └── resources/
│   │       ├── application.yml                 # 应用配置
│   │       ├── logback-spring.xml             # 日志配置
│   │       └── schema.sql                      # 数据库初始化
│   │
│   └── test/                                   # 测试代码
│       ├── java/
│       │   └── com/example/rag/
│       │       └── service/
│       │           ├── DocumentParserServiceTest.java  # 文档解析测试
│       │           └── VectorServiceTest.java         # 向量服务测试
│       │
│       └── resources/
│           ├── application-test.yml            # 测试配置
│           └── test-documents/
│               └── sample.txt                 # 测试文档样本
│
├── docs/                                      # 文档目录
│   ├── README.md                              # 文档首页
│   ├── API_TEST_CASES.md                      # API测试用例
│   ├── APACHE_TIKA_INTEGRATION.md             # Tika集成说明
│   ├── DOCUMENT_PROCESSING_WORKFLOW.md        # 文档处理流程
│   ├── TESTING_GUIDE.md                       # 测试指南
│   └── DEPLOYMENT_GUIDE.md                    # 部署指南
│
├── deployment/                                # 部署配置
│   ├── deploy.sh                              # 一键部署脚本
│   ├── start.sh                               # 启动脚本
│   ├── docker-compose.yml                     # Docker编排
│   ├── Dockerfile                             # Docker镜像
│   ├── config/                               # 配置文件
│   │   ├── application-prod.yml
│   │   └── nginx/rag.conf
│   └── scripts/
│       ├── backup.sh
│       └── restore.sh
│
├── uploads/                                  # 上传文件目录
│   └── documents/                            # 文档存储
│
├── logs/                                     # 日志文件目录
│
├── pom.xml                                   # Maven配置
├── mvnw                                      # Maven Wrapper (Unix)
├── mvnw.cmd                                 # Maven Wrapper (Windows)
├── Dockerfile                                # Docker构建文件
├── docker-compose.yml                        # Docker编排
├── init.sql                                 # 数据库初始化
├── README.md                                # 项目说明
├── .gitignore                               # Git忽略文件
├── .gitattributes                           # Git属性
└── .env.example                             # 环境变量示例
```

---

## 🎯 各层职责

### 1. Controller 层（控制层）

**职责**：处理 HTTP 请求和响应

**位置**：`src/main/java/com/example/rag/controller/`

**类列表**：
- `BaseController` - 控制器基类，提供通用方法
- `AuthController` - 用户认证（登录、注册）
- `UserController` - 用户管理（CRUD）
- `DocumentController` - 文档管理（上传、查询、删除）
- `KnowledgeBaseController` - 知识库管理
- `QAController` - 问答接口
- `AnalyticsController` - 运营分析

**注解示例**：
```java
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "文档管理", description = "文档上传和管理接口")
@PreAuthorize("hasAnyRole('ADMIN', 'KNOWLEDGE_BASE_ADMIN')")
public class DocumentController extends BaseController {
    // ...
}
```

---

### 2. Service 层（业务逻辑层）

**职责**：处理业务逻辑，调用多个 Repository

**位置**：`src/main/java/com/example/rag/service/`

**类列表**：
- `UserService` - 用户注册、登录、管理
- `DocumentService` - 文档上传、处理、查询、删除
- `DocumentParserService` - Apache Tika 文档解析
- `VectorService` - 向量生成、存储、检索
- `QAService` - RAG 问答处理
- `KnowledgeBaseService` - 知识库管理

**代码示例**：
```java
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final VectorService vectorService;
    private final DocumentParserService documentParserService;

    @Transactional
    public DocumentResponse uploadDocument(MultipartFile file, UUID knowledgeBaseId, UUID userId) {
        // 业务逻辑
    }
}
```

---

### 3. Repository 层（数据访问层）

**职责**：与数据库交互，使用 Spring Data JPA

**位置**：`src/main/java/com/example/rag/repository/`

**类列表**：
- `UserRepository` - 用户数据访问
- `DocumentRepository` - 文档数据访问
- `DocumentChunkRepository` - 文档块数据访问
- `DocumentChunkVectorRepository` - 向量检索（原生SQL）
- `KnowledgeBaseRepository` - 知识库数据访问
- `QuestionRepository` - 问题数据访问
- `AnswerRepository` - 答案数据访问

**代码示例**：
```java
@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByKnowledgeBaseId(UUID knowledgeBaseId, Pageable pageable);

    Page<Document> findByTitleContaining(String title, Pageable pageable);

    Page<Document> findByKnowledgeBaseIdAndTitleContaining(
        UUID knowledgeBaseId, String title, Pageable pageable);
}
```

---

### 4. Entity 层（数据实体）

**职责**：定义数据库表结构

**位置**：`src/main/java/com/example/rag/entity/`

**主要实体**：
- `User` - 用户表
- `Document` - 文档表
- `DocumentChunk` - 文档块表（含向量）
- `KnowledgeBase` - 知识库表
- `Question` - 问题记录表
- `Answer` - 答案记录表

**代码示例**：
```java
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    private String filePath;
    private String fileType;
    private Integer fileSize;

    @Enumerated(EnumType.STRING)
    private Status status;

    // ...
}
```

---

### 5. DTO 层（数据传输对象）

**职责**：封装请求和响应数据

**位置**：
- 请求DTO：`src/main/java/com/example/rag/dto/request/`
- 响应DTO：`src/main/java/com/example/rag/dto/response/`

**主要类**：
- `LoginRequest` - 登录请求
- `LoginResponse` - 登录响应
- `ApiResponse<T>` - 统一响应格式
- `PagedResponse<T>` - 分页响应
- `DocumentResponse` - 文档响应
- `AnswerResponse` - 回答响应

**代码示例**：
```java
@Data
@Builder
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }
}
```

---

## 🔧 配置层

### 1. 安全配置

**文件**：`src/main/java/com/example/rag/config/SecurityConfig.java`

**功能**：
- 配置 JWT 认证
- 配置 CORS 跨域
- 配置端点权限
- 配置密码加密

### 2. 应用配置

**文件**：`src/main/resources/application.yml`

**配置项**：
- 数据库连接
- Redis 配置
- JWT 配置
- 文件上传配置
- 日志配置

### 3. 日志配置

**文件**：`src/main/resources/logback-spring.xml`

**功能**：
- 分级日志输出
- 日志文件滚动
- 请求日志拦截
- 链路追踪集成

---

## 🧪 测试层

### 测试目录结构

```
src/test/
├── java/
│   └── com/example/rag/
│       └── service/
│           ├── DocumentParserServiceTest.java    # 文档解析测试
│           └── VectorServiceTest.java            # 向量服务测试
│
└── resources/
    ├── application-test.yml                       # 测试配置
    └── test-documents/
        └── sample.txt                            # 测试文档
```

### 测试类列表

| 测试类 | 测试数量 | 覆盖内容 |
|--------|---------|---------|
| `DocumentParserServiceTest` | 26 | Apache Tika 文档解析 |
| `VectorServiceTest` | 18 | 向量生成和管理 |
| **总计** | **44** | - |

---

## 📦 Maven 依赖

### 核心依赖

```xml
<!-- Spring Boot -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- PostgreSQL + pgvector -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>

<!-- Apache Tika -->
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>3.3.0</version>
</dependency>

<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parsers-standard-package</artifactId>
    <version>3.3.0</version>
</dependency>

<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.5</version>
</dependency>
```

---

## 🚀 构建和运行

### 构建项目

```bash
# 开发构建
mvn clean compile

# 测试构建
mvn clean test

# 生产构建
mvn clean package -DskipTests

# 使用 Maven Wrapper
./mvnw clean package -DskipTests
```

### 运行项目

```bash
# 开发模式
mvn spring-boot:run

# 生产模式
java -jar target/rag-1.0.0.jar
```

### Docker 构建

```bash
# 构建镜像
docker build -t rag-backend .

# 运行容器
docker run -d -p 8080:8080 rag-backend
```

---

## 📊 关键文件说明

| 文件 | 位置 | 说明 |
|------|------|------|
| **pom.xml** | 根目录 | Maven 依赖配置 |
| **mvnw** | 根目录 | Maven Wrapper（Unix） |
| **mvnw.cmd** | 根目录 | Maven Wrapper（Windows） |
| **init.sql** | 根目录 | 数据库初始化脚本 |
| **Dockerfile** | 根目录 | Docker 镜像构建 |
| **docker-compose.yml** | 根目录 | Docker 服务编排 |
| **README.md** | 根目录 | 项目说明文档 |
| **.env.example** | 根目录 | 环境变量示例 |

---

## 🎯 开发规范

### 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 类名 | UpperCamelCase | `DocumentService` |
| 方法名 | lowerCamelCase | `uploadDocument()` |
| 变量名 | lowerCamelCase | `documentId` |
| 常量 | UPPER_SNAKE_CASE | `MAX_FILE_SIZE` |
| 包名 | 小写 | `com.example.rag` |

### 分层规范

```
请求 → Controller → Service → Repository → 数据库
              ↓
          Exception Handler（统一异常处理）
```

### 代码组织

```
每个包（package）应包含：
1. 相关的类文件
2. package-info.java（包说明）
```

---

## 📚 相关文档

- [API测试用例](./API_TEST_CASES.md) - REST API 测试
- [Apache Tika集成](./APACHE_TIKA_INTEGRATION.md) - 文档解析说明
- [文档处理流程](./DOCUMENT_PROCESSING_WORKFLOW.md) - 完整处理流程
- [测试指南](./TESTING_GUIDE.md) - 如何编写和运行测试
- [部署指南](./DEPLOYMENT_GUIDE.md) - 部署说明

---

## ✅ 工程检查清单

新建或修改代码时，确保：

- [ ] 遵循上述分层规范
- [ ] 添加必要的注释
- [ ] 为新功能添加单元测试
- [ ] 更新相关文档
- [ ] 通过所有测试：`mvn test`
- [ ] 代码格式化：`mvn spotless:apply`
