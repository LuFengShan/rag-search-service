# Contributing to RAG Knowledge Base

感谢您有兴趣为 RAG 知识库智能问答系统做出贡献！

## 📋 贡献流程

### 1. Fork 项目

点击 GitHub 页面上的 "Fork" 按钮创建项目副本。

### 2. 克隆仓库

```bash
git clone https://github.com/your-username/rag-knowledge-base.git
cd rag-knowledge-base/backend
```

### 3. 创建功能分支

```bash
git checkout -b feature/your-feature-name
```

### 4. 开发

- 编写代码
- 添加测试
- 更新文档

### 5. 提交代码

```bash
git add .
git commit -m "feat: add new feature"
git push origin feature/your-feature-name
```

### 6. 创建 Pull Request

在 GitHub 上创建 Pull Request 并描述您的更改。

## 📝 代码规范

### Java 代码规范

- 遵循 Google Java Style Guide
- 使用 Lombok 简化代码
- 使用 SLF4J 进行日志记录
- 异常处理统一

### 提交信息规范

使用 Conventional Commits 格式：

```
feat: 添加新功能
fix: 修复 bug
docs: 更新文档
style: 代码格式化
refactor: 重构代码
test: 添加测试
chore: 构建/工具相关更改
```

## 🧪 测试要求

- 所有新功能必须添加单元测试
- 测试覆盖率至少达到 80%
- 运行测试：`./mvnw test`

## 🔧 开发环境

### 依赖

- JDK 21+
- Maven 3.6+
- PostgreSQL 15+ (带 pgvector)
- Redis 6+

### 启动开发服务器

```bash
# 启动数据库（使用 Docker）
docker compose up -d

# 构建并运行
./mvnw spring-boot:run
```

## 📄 许可证

贡献的代码将遵循 MIT License。

## 📧 联系我们

如有问题，请提交 Issue 或发送邮件至开发团队。
