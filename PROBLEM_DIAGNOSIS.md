# 问题诊断报告：JWT认证失败

## 问题确认

经过测试和数据库查询，确认问题如下：

### 1. 数据库数据正常

```bash
$ PGPASSWORD=123456 psql -h localhost -U postgres -d example_db \
  -c "SELECT id, username, email, role FROM users WHERE username = 'admin';"

                  id                  | username |       email       | role  
--------------------------------------+----------+-------------------+-------
 40d4b644-5e15-4ab9-afbd-ccf57f3c957c | admin    | admin@example.com | ADMIN
(1 row)
```

数据库中 admin 用户有正确的 UUID id: `40d4b644-5e15-4ab9-afbd-ccf57f3c957c`

### 2. 问题出在 MyBatis-Plus 查询结果映射

UserMapper.java 使用 @Select 注解进行查询：

```java
@Select("SELECT * FROM users WHERE username = #{username}")
Optional<User> findByUsername(@Param("username") String username);
```

虽然 User 实体配置了 `@TableName` 和 `@TableId(type = IdType.ASSIGN_UUID)`，但是使用 @Select 注解的查询不会自动使用实体类上配置的结果映射。

### 3. 解决方案

#### 方案1：在实体类上添加 autoResultMap = true (推荐)

修改 User.java：

```java
@TableName(value = "users", autoResultMap = true)
public class User {
    // ...
}
```

然后需要创建对应的 Mapper XML 文件来定义结果映射。

#### 方案2：创建 Mapper XML 文件

创建 UserMapper.xml：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" 
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.rag.mapper.UserMapper">
    
    <resultMap id="BaseResultMap" type="com.example.rag.entity.User">
        <id column="id" property="id" typeHandler="com.baomidou.mybatisplus.core.handlers.JacksonTypeHandler"/>
        <result column="username" property="username"/>
        <result column="email" property="email"/>
        <result column="password_hash" property="passwordHash"/>
        <result column="role" property="role"/>
        <result column="created_at" property="createdAt"/>
        <result column="updated_at" property="updatedAt"/>
    </resultMap>
    
</mapper>
```

并修改 UserMapper.java：

```java
@Select("SELECT * FROM users WHERE username = #{username}")
@ResultMap("BaseResultMap")
Optional<User> findByUsername(@Param("username") String username);
```

#### 方案3：修改 UserMapper 使用 MyBatis-Plus 内置方法

删除 @Select 注解，直接使用 MyBatis-Plus 的 selectOne 方法：

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {
    
    default Optional<User> findByUsername(String username) {
        return Optional.ofNullable(selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        ));
    }
}
```

这样 MyBatis-Plus 会自动使用实体类的配置进行结果映射。

## 验证步骤

修复后，执行以下验证：

### 1. 重新启动服务

```bash
cd /Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service
./mvnw spring-boot:run
```

或者使用已编译的 jar：

```bash
java -jar target/rag-search-service-1.0.0.jar
```

### 2. 测试登录接口

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

预期结果：
```json
{
  "success": true,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": "40d4b644-5e15-4ab9-afbd-ccf57f3c957c",
    "username": "admin",
    "email": "admin@example.com",
    "role": "ADMIN"
  }
}
```

注意：`userId` 字段应该是一个有效的 UUID，不再是 `null`。

### 3. 测试需要认证的接口

```bash
# 获取用户列表
curl http://localhost:8080/api/admin/users \
  -H "Authorization: Bearer <token>"

# 创建知识库
curl -X POST http://localhost:8080/api/knowledge \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"测试知识库","description":"描述"}'
```

预期：这些接口应该返回 200 而不是 403。

## 额外发现

### 用户注册问题

注册接口也失败了，错误信息相同：

```
Type handler was null on parameter mapping for property 'id'
```

这个问题也是因为 MyBatis-Plus 的 UUID 类型处理问题。在 createUser 时，插入操作需要正确处理 UUID。

**修复方案**：确保实体类的 @TableId 配置正确，并检查 MyBatis-Plus 的全局配置。

## 相关文件

- [User.java](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service/src/main/java/com/example/rag/entity/User.java) - 实体类
- [UserMapper.java](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service/src/main/java/com/example/rag/mapper/UserMapper.java) - Mapper接口
- [UserService.java](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service/src/main/java/com/example/rag/service/UserService.java) - 业务逻辑

---
*诊断时间: 2026-05-18*
