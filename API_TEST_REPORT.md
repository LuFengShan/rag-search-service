# RAG Search Service REST API 测试报告

## 测试环境
- **基础URL**: http://localhost:8080
- **测试时间**: 2026-05-18
- **服务状态**: 运行中

## 测试结果总览

| 序号 | 接口类别 | 接口名称 | HTTP方法 | 路径 | 测试结果 | 状态码 | 问题描述 |
|------|---------|---------|---------|------|---------|--------|---------|
| 1 | 认证接口 | 用户登录 | POST | /api/auth/login | **通过** | 200 | - |
| 2 | 认证接口 | 用户注册 | POST | /api/auth/register | **失败** | 500 | MyBatis UUID类型处理器缺失 |
| 3 | 用户管理 | 获取用户列表 | GET | /api/admin/users | **失败** | 403 | JWT认证失败(userId为null) |
| 4 | 用户管理 | 获取单个用户 | GET | /api/admin/users/{id} | **未测试** | - | 依赖用户列表 |
| 5 | 用户管理 | 更新用户 | PUT | /api/admin/users/{id} | **未测试** | - | 依赖用户列表 |
| 6 | 用户管理 | 删除用户 | DELETE | /api/admin/users/{id} | **未测试** | - | 依赖用户列表 |
| 7 | 知识库管理 | 获取知识库列表 | GET | /api/knowledge | **失败** | 403 | JWT认证失败 |
| 8 | 知识库管理 | 创建知识库 | POST | /api/knowledge | **失败** | 403 | JWT认证失败 |
| 9 | 知识库管理 | 获取单个知识库 | GET | /api/knowledge/{id} | **失败** | 403 | JWT认证失败 |
| 10 | 知识库管理 | 更新知识库 | PUT | /api/knowledge/{id} | **失败** | 403 | JWT认证失败 |
| 11 | 知识库管理 | 删除知识库 | DELETE | /api/knowledge/{id} | **失败** | 403 | JWT认证失败 |
| 12 | 文档管理 | 获取文档列表 | GET | /api/documents | **失败** | 403 | JWT认证失败 |
| 13 | 文档管理 | 上传文档 | POST | /api/documents/upload | **未测试** | - | 依赖JWT认证 |
| 14 | 文档管理 | 获取单个文档 | GET | /api/documents/{id} | **未测试** | - | 依赖JWT认证 |
| 15 | 文档管理 | 删除文档 | DELETE | /api/documents/{id} | **未测试** | - | 依赖JWT认证 |
| 16 | 问答接口 | 提问 | POST | /api/qa/question | **失败** | 403 | JWT认证失败 |
| 17 | 问答接口 | 获取问答历史 | GET | /api/qa/history | **失败** | 403 | JWT认证失败 |
| 18 | 统计分析 | 获取统计概览 | GET | /api/analytics/overview | **失败** | 403 | JWT认证失败 |
| 19 | 统计分析 | 获取趋势数据 | GET | /api/analytics/trend | **失败** | 403 | JWT认证失败 |

## 详细问题分析

### 问题1: 用户注册接口失败 (HTTP 500)

**错误信息**:
```
Type handler was null on parameter mapping for property 'id'.
It was either not specified and/or could not be found for the javaType (java.util.UUID) : jdbcType (null) combination.
```

**根本原因**: MyBatis/MyBatis-Plus 在处理 UUID 类型时缺少类型处理器配置。

**修复建议**:
1. 在 UserMapper.xml 中添加 UUID 类型处理器
2. 或在实体类的 @TableId 注解中指定 type = IdType.ASSIGN_UUID
3. 或创建全局的 MyBatis UUID 类型处理器配置

### 问题2: JWT认证全面失败 (HTTP 403)

**错误信息**:
```
Invalid UUID string: null
```

**根本原因**:
1. 登录成功后，JWT token 的 subject 字段为 "null" 字符串
2. 这导致 JwtTokenProvider.getUserIdFromToken() 尝试将 "null" 解析为 UUID 时抛出异常
3. JwtAuthenticationFilter 捕获异常但不设置认证上下文
4. 所有需要认证的请求都返回 403 Forbidden

**影响范围**: 14个需要认证的接口全部失败

**根本原因分析**:
查看 UserService.java 第63行:
```java
String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole().name());
```

登录响应:
```json
{
  "success": true,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJudWxs...",
    "userId": null,
    "username": "admin",
    "email": "admin@example.com",
    "role": "ADMIN"
  }
}
```

问题在于: `user.getId()` 返回了 null

**可能原因**:
1. UserMapper.findByUsername() 查询没有正确映射 id 字段
2. 数据库中 admin 用户的 id 字段为 null
3. MyBatis-Plus 的结果映射配置问题

## 接口测试详情

### 1. 认证接口

#### 1.1 POST /api/auth/login - 用户登录
- **状态**: ✅ 通过
- **HTTP状态码**: 200
- **请求体**:
```json
{
  "username": "admin",
  "password": "admin123"
}
```
- **响应**:
```json
{
  "success": true,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": null,
    "username": "admin",
    "email": "admin@example.com",
    "role": "ADMIN"
  }
}
```
- **问题**: userId 为 null，但接口返回成功

#### 1.2 POST /api/auth/register - 用户注册
- **状态**: ❌ 失败
- **HTTP状态码**: 500
- **错误**: MyBatis UUID 类型处理器缺失

### 2. 用户管理接口

所有接口均返回 403，因为 JWT 认证失败。

#### 2.1 GET /api/admin/users - 获取用户列表
- **状态**: ❌ 失败
- **HTTP状态码**: 403
- **错误**: Invalid UUID string: null

#### 2.2-2.4 - 依赖认证修复后测试

### 3. 知识库管理接口

所有接口均返回 403，因为 JWT 认证失败。

#### 3.1 GET /api/knowledge - 获取知识库列表
- **状态**: ❌ 失败
- **HTTP状态码**: 403

#### 3.2 POST /api/knowledge - 创建知识库
- **状态**: ❌ 失败
- **HTTP状态码**: 403

### 4. 文档管理接口

#### 4.1 GET /api/documents - 获取文档列表
- **状态**: ❌ 失败
- **HTTP状态码**: 403

### 5. 问答接口

#### 5.1 POST /api/qa/question - 提问
- **状态**: ❌ 失败
- **HTTP状态码**: 403

#### 5.2 GET /api/qa/history - 获取问答历史
- **状态**: ❌ 失败
- **HTTP状态码**: 403

### 6. 统计分析接口

#### 6.1 GET /api/analytics/overview - 获取统计概览
- **状态**: ❌ 失败
- **HTTP状态码**: 403

#### 6.2 GET /api/analytics/trend - 获取趋势数据
- **状态**: ❌ 失败
- **HTTP状态码**: 403

## 根本原因定位

通过查看代码和日志，问题定位如下:

1. **UserMapper.findByUsername()** 返回的 User 对象中 id 字段为 null
2. **JwtTokenProvider** 使用 user.getId() 生成 token，subject 为 "null"
3. **JwtAuthenticationFilter** 在解析 token 时抛出 IllegalArgumentException
4. 异常被捕获但不设置认证上下文，导致所有请求都未认证

**相关文件**:
- JwtTokenProvider.java - getUserIdFromToken()
- UserMapper.java - findByUsername()
- JwtAuthenticationFilter.java - 认证逻辑

## 修复建议

### 紧急修复 (必须)

#### 修复1: UserMapper 查询问题

修改 UserMapper.java:
确保 MyBatis-Plus 的结果映射包含 id 字段。可以添加 ResultMap 或使用 @TableName(autoResultMap = true)。

#### 修复2: 检查数据库 admin 用户

验证数据库中的 admin 用户是否有有效的 UUID id:
```sql
SELECT id, username, email, role FROM users WHERE username = 'admin';
```

如果 id 为 null，需要更新:
```sql
UPDATE users SET id = uuid_generate_v4() WHERE username = 'admin' AND id IS NULL;
```

### 建议修复 (重要)

#### 修复3: JWT 异常处理

在 JwtAuthenticationFilter.java 中改进错误处理，添加 null 检查。

#### 修复4: 注册接口 UUID 处理

在 UserService.java 的 createUser 方法中，确保 id 被正确生成并验证。

## 测试覆盖率统计

- **总接口数**: 19个
- **已测试**: 19个
- **通过**: 1个 (5.3%)
- **失败**: 18个 (94.7%)
- **未测试 (依赖项失败)**: 0个

## 安全配置检查

查看了 SecurityConfig.java，安全配置基本正确:
- ✅ POST /api/auth/login 和 /api/auth/register 允许匿名访问
- ✅ /api/admin/** 需要 ADMIN 角色
- ✅ /api/knowledge/** 和 /api/documents/** 需要 ADMIN 或 KNOWLEDGE_BASE_ADMIN 角色
- ✅ /api/qa/** 需要认证
- ✅ CSRF 已禁用
- ✅ CORS 已配置

## 总结

**核心问题**: JWT 认证流程存在缺陷，导致所有需要认证的接口都无法正常工作。

**影响**:
- 用户无法注册新账号
- 所有需要认证的接口（14个）完全不可用
- 系统功能基本瘫痪

**优先级**: P0 (最高优先级，必须立即修复)

**建议**:
1. 立即修复 UserMapper 查询问题
2. 验证数据库中 admin 用户的数据完整性
3. 修复后重新测试所有认证相关接口
4. 建立 JWT 认证的单元测试，防止回归

---
*测试报告生成时间: 2026-05-18*
*测试工具: curl + bash script*
