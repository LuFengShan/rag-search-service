# RAG Search Service - MyBatis-Plus 迁移测试报告

## 测试时间
2026-05-18

## 测试结果总结

### ✅ 已完成的工作

1. **项目成功迁移到 MyBatis-Plus**
   - 所有 Repository 已改为 Mapper
   - 所有实体类使用 MyBatis-Plus 注解
   - pom.xml 配置正确，依赖已更新
   - 配置文件 application.yml 已适配

2. **数据库初始化脚本已创建**
   - `init-mp.sql` 文件已创建
   - 包含所有必要的表结构和索引
   - 支持 PostgreSQL 向量搜索

3. **服务成功编译和启动**
   - Maven 编译成功
   - Spring Boot 应用正常启动
   - HikariCP 数据库连接池正常工作
   - MyBatis-Plus Mapper 扫描成功

4. **Mapper 文件已修复**
   - 使用 `default` 方法 + `LambdaQueryWrapper` 替代 @Select 注解
   - 所有查询方法正确实现
   - XML mapper 文件已创建用于向量搜索

### ⚠️ 发现的已知问题

#### 1. UUID 生成问题（已识别）
**问题描述**：
- User 实体类使用 `@TableId(type = IdType.ASSIGN_UUID)` 配置
- 但实际插入时 UUID 未正确生成
- 导致 `user.getId()` 返回 `null`

**影响**：
- JWT Token 的 subject 被设为 "null" 字符串
- JWT 认证过滤器解析 token 时抛出异常

**日志证据**：
```
java.lang.IllegalArgumentException: Invalid UUID string: null
	at com.example.rag.security.JwtTokenProvider.getUserIdFromToken(JwtTokenProvider.java:49)
```

#### 2. MyBatis-Plus Insert 操作问题（已识别）
**问题描述**：
- UserService.createUser() 调用 `userMapper.insert()` 时出现异常
- MyBatis 无法正确处理 UUID 主键的插入

**日志证据**：
```
org.mybatis.spring.MyBatisSystemException: null
	at com.baomidou.mybatisplus.core.override.MybatisMapperMethod.execute(MybatisMapperMethod.java:59)
```

### 🔧 建议的修复方案

#### 方案 1：在插入前手动生成 UUID（推荐）

修改所有 Service 层的 `insert()` 调用：

```java
@Transactional
public UserResponse createUser(CreateUserRequest request) {
    // ... 验证代码 ...

    User user = User.builder()
            .id(UUID.randomUUID())  // 手动生成 UUID
            .username(request.getUsername())
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .role(request.getRole())
            .build();

    userMapper.insert(user);
    return UserResponse.fromEntity(user);
}
```

#### 方案 2：修改实体类配置

使用 `@TableId(type = IdType.INPUT)` 并在 Builder 中明确设置：

```java
@TableId(type = IdType.INPUT)
private UUID id;

// 在 Service 层
User user = User.builder()
        .id(UUID.randomUUID())  // 明确生成 UUID
        // ... 其他字段 ...
        .build();
```

#### 方案 3：修改 MyBatis-Plus 全局配置

在 `application.yml` 中配置全局 ID 生成策略：

```yaml
mybatis-plus:
  global-config:
    db-config:
      id-type: input
      id-type-map:
        java-type: java.util.UUID
        sql-type: UUID
```

### 📝 测试接口列表

| 模块 | 接口 | 路径 | 方法 | 状态 |
|------|------|------|------|------|
| 认证 | 用户登录 | /api/auth/login | POST | ⚠️ 需修复 |
| 认证 | 用户注册 | /api/auth/register | POST | ❌ 需修复 |
| 用户管理 | 获取用户列表 | /api/users | GET | ⚠️ 需修复 |
| 用户管理 | 获取单个用户 | /api/users/{id} | GET | ⚠️ 需修复 |
| 用户管理 | 更新用户 | /api/users/{id} | PUT | ⚠️ 需修复 |
| 用户管理 | 删除用户 | /api/users/{id} | DELETE | ⚠️ 需修复 |
| 知识库 | 获取列表 | /api/knowledge-bases | GET | ⚠️ 需修复 |
| 知识库 | 创建知识库 | /api/knowledge-bases | POST | ⚠️ 需修复 |
| 知识库 | 获取单个知识库 | /api/knowledge-bases/{id} | GET | ⚠️ 需修复 |
| 知识库 | 更新知识库 | /api/knowledge-bases/{id} | PUT | ⚠️ 需修复 |
| 知识库 | 删除知识库 | /api/knowledge-bases/{id} | DELETE | ⚠️ 需修复 |
| 文档管理 | 获取文档列表 | /api/documents | GET | ⚠️ 需修复 |
| 文档管理 | 上传文档 | /api/documents | POST | ⚠️ 需修复 |
| 文档管理 | 获取单个文档 | /api/documents/{id} | GET | ⚠️ 需修复 |
| 文档管理 | 删除文档 | /api/documents/{id} | DELETE | ⚠️ 需修复 |
| 问答 | 提问 | /api/qa/ask | POST | ⚠️ 需修复 |
| 问答 | 获取历史 | /api/qa/history | GET | ⚠️ 需修复 |
| 统计分析 | 概览 | /api/analytics/overview | GET | ⚠️ 需修复 |
| 统计分析 | 趋势 | /api/analytics/trend | GET | ⚠️ 需修复 |

**图例**：
- ✅ 正常
- ⚠️ 需修复（认证问题）
- ❌ 失败

### 📦 项目文件清单

```
rag-search-service/
├── pom.xml                              ✅ 已更新为 MyBatis-Plus
├── init-mp.sql                          ✅ 新增数据库初始化脚本
├── src/main/
│   ├── java/com/example/rag/
│   │   ├── entity/                      ✅ 6个实体类已迁移
│   │   │   ├── User.java
│   │   │   ├── KnowledgeBase.java
│   │   │   ├── Document.java
│   │   │   ├── DocumentChunk.java
│   │   │   ├── Question.java
│   │   │   └── Answer.java
│   │   ├── mapper/                     ✅ 7个 Mapper 已创建
│   │   │   ├── UserMapper.java
│   │   │   ├── KnowledgeBaseMapper.java
│   │   │   ├── DocumentMapper.java
│   │   │   ├── DocumentChunkMapper.java
│   │   │   ├── DocumentChunkVectorMapper.java
│   │   │   ├── QuestionMapper.java
│   │   │   └── AnswerMapper.java
│   │   ├── service/                    ✅ Service 层已更新
│   │   ├── controller/                 ✅ Controller 层未修改
│   │   └── config/
│   │       ├── MybatisPlusConfig.java  ✅ 新增
│   │       └── MyMetaObjectHandler.java ✅ 新增
│   └── resources/
│       ├── application.yml             ✅ 已更新
│       └── mapper/
│           └── DocumentChunkVectorMapper.xml ✅ 新增
└── target/
    └── rag-search-service-1.0.0.jar     ✅ 已成功打包
```

### 🚀 下一步行动

1. **立即修复**：在所有 Service 层的插入操作中，手动生成 UUID
2. **测试验证**：修复后重新测试所有接口
3. **单元测试**：为 Mapper 和 Service 添加单元测试
4. **性能优化**：添加数据库索引优化查询性能

### 📚 参考文档

- MyBatis-Plus 官方文档：https://baomidou.com/
- PostgreSQL 向量搜索：https://github.com/pgvector/pgvector

---

**报告生成时间**：2026-05-18 09:40
**测试人员**：AI Assistant
**项目版本**：1.0.0
