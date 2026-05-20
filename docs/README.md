# RAG 知识库系统 - 项目文档索引

本文档汇总了项目中所有重要文档的索引，方便快速查找。

## 📚 文档总览

| 文档名称 | 路径 | 说明 |
|---------|------|------|
| **项目说明** | [README.md](../README.md) | 项目简介、技术栈、快速开始 |
| **Maven工程结构** | [docs/MAVEN_PROJECT_STRUCTURE.md](./MAVEN_PROJECT_STRUCTURE.md) | 完整的项目目录结构说明 |
| **API测试用例** | [docs/API_TEST_CASES.md](./API_TEST_CASES.md) | Python3实现的44个测试用例 |
| **测试指南** | [docs/TESTING_GUIDE.md](./TESTING_GUIDE.md) | 如何运行和维护测试 |
| **Apache Tika集成** | [docs/APACHE_TIKA_INTEGRATION.md](./APACHE_TIKA_INTEGRATION.md) | 文档解析库的使用说明 |
| **文档处理流程** | [docs/DOCUMENT_PROCESSING_WORKFLOW.md](./DOCUMENT_PROCESSING_WORKFLOW.md) | 文档上传→分块→向量化→入库 |
| **快速参考** | [docs/DOCUMENT_PROCESSING_QUICK_REFERENCE.md](./DOCUMENT_PROCESSING_QUICK_REFERENCE.md) | 文档处理的快速参考 |
| **架构与技术栈** | [docs/ARCHITECTURE_AND_TECH_STACK.md](./ARCHITECTURE_AND_TECH_STACK.md) | 完整的系统架构和技术栈说明 |
| **部署指南** | [deployment/README.md](../deployment/README.md) | 部署相关文档索引 |

---

## 🎯 按主题分类

### 🚀 快速开始

1. **[README.md](../README.md)** - 项目整体介绍
   - 技术栈说明
   - 环境要求
   - 快速开始步骤
   - 示例代码

### 📁 项目结构

2. **[MAVEN_PROJECT_STRUCTURE.md](./MAVEN_PROJECT_STRUCTURE.md)** - Maven工程完整结构
   - 目录结构详解
   - 各层职责说明
   - 代码规范
   - 构建和运行

### 🧪 测试相关

3. **[API_TEST_CASES.md](./API_TEST_CASES.md)** - REST API 测试用例
   - 44个Python3测试用例
   - 6个测试模块
   - 认证、权限、边界测试
   - CI/CD集成示例

4. **[TESTING_GUIDE.md](./TESTING_GUIDE.md)** - 测试运行和维护指南
   - 测试统计和概览
   - 运行测试命令
   - 添加新测试
   - 故障排查

### 📄 文档处理

5. **[DOCUMENT_PROCESSING_WORKFLOW.md](./DOCUMENT_PROCESSING_WORKFLOW.md)** - 完整处理流程
   - 四步流程详解
   - Apache Tika集成
   - 智能分块策略
   - 时序图和代码

6. **[APACHE_TIKA_INTEGRATION.md](./APACHE_TIKA_INTEGRATION.md)** - Tika使用说明
   - 支持的文档格式
   - 核心API说明
   - 分块策略
   - 配置和故障排查

7. **[DOCUMENT_PROCESSING_QUICK_REFERENCE.md](./DOCUMENT_PROCESSING_QUICK_REFERENCE.md)** - 快速参考卡
   - 核心流程速览
   - 关键代码路径
   - 测试命令

### 🏗️ 架构相关

8. **[ARCHITECTURE_AND_TECH_STACK.md](./ARCHITECTURE_AND_TECH_STACK.md)** - 完整系统架构和技术栈
   - 整体架构图
   - 所有技术栈详解
   - 数据模型说明
   - 关键技术决策

### 🚢 部署相关

9. **[deployment/README.md](../deployment/README.md)** - 部署文档索引
   - 单机部署指南
   - Docker部署指南
   - 一键部署脚本
   - 配置说明

---

## 📖 核心功能文档

### 1. 文档上传和处理

**核心流程**：
```
上传文档 → Tika解析 → 智能分块 → 向量化 → 存入pgvector
```

**相关文档**：
- [APACHE_TIKA_INTEGRATION.md](./APACHE_TIKA_INTEGRATION.md) - Tika使用
- [DOCUMENT_PROCESSING_WORKFLOW.md](./DOCUMENT_PROCESSING_WORKFLOW.md) - 完整流程
- [DOCUMENT_PROCESSING_QUICK_REFERENCE.md](./DOCUMENT_PROCESSING_QUICK_REFERENCE.md) - 快速参考

**关键代码**：
- `DocumentParserService` - 文档解析
- `DocumentService` - 文档处理
- `VectorService` - 向量服务

### 2. RAG 问答

**核心流程**：
```
用户提问 → 向量化 → 相似度检索 → 生成回答 → 返回结果
```

**相关文档**：
- [DOCUMENT_PROCESSING_WORKFLOW.md](./DOCUMENT_PROCESSING_WORKFLOW.md#L3) - 向量检索部分
- [API_TEST_CASES.md](./API_TEST_CASES.md#L450) - 问答接口测试

**关键代码**：
- `QAService` - RAG问答逻辑
- `VectorService` - 向量检索
- `QAController` - 问答API

### 3. 安全认证

**核心流程**：
```
登录请求 → JWT验证 → 生成令牌 → 返回用户信息
```

**相关文档**：
- `SecurityConfig.java` - 安全配置
- [API_TEST_CASES.md](./API_TEST_CASES.md#L120) - 认证测试

**关键代码**：
- `JwtTokenProvider` - JWT令牌管理
- `JwtAuthenticationFilter` - JWT过滤器
- `BaseController` - 权限验证基类

---

## 🔍 快速查找

### 查找特定功能的文档

| 功能 | 文档 | 关键位置 |
|------|------|---------|
| 如何上传文档 | [APACHE_TIKA_INTEGRATION.md](./APACHE_TIKA_INTEGRATION.md) | 使用示例部分 |
| 如何测试接口 | [API_TEST_CASES.md](./API_TEST_CASES.md) | 各模块测试 |
| 如何配置CORS | `SecurityConfig.java` | CORS配置部分 |
| 如何添加新测试 | [TESTING_GUIDE.md](./TESTING_GUIDE.md) | 添加新测试部分 |
| 如何部署服务 | `deployment/` 目录 | 各种部署方式 |

### 查找特定代码的文档

| 代码 | 相关文档 |
|------|---------|
| `DocumentService` | [DOCUMENT_PROCESSING_WORKFLOW.md](./DOCUMENT_PROCESSING_WORKFLOW.md) |
| `VectorService` | [DOCUMENT_PROCESSING_WORKFLOW.md](./DOCUMENT_PROCESSING_WORKFLOW.md) |
| `QAService` | [DOCUMENT_PROCESSING_WORKFLOW.md](./DOCUMENT_PROCESSING_WORKFLOW.md) |
| `DocumentParserService` | [APACHE_TIKA_INTEGRATION.md](./APACHE_TIKA_INTEGRATION.md) |

---

## 📝 文档更新日志

### 2026-05-17

- ✅ 完成Maven工程结构文档
- ✅ 完成测试代码（44个测试）
- ✅ 完成API测试用例文档
- ✅ 完成测试指南
- ✅ 完成Apache Tika集成文档
- ✅ 完成文档处理流程文档

---

## 🎯 下一步

### 新手入门

1. 阅读 [README.md](../README.md) 了解项目
2. 阅读 [MAVEN_PROJECT_STRUCTURE.md](./MAVEN_PROJECT_STRUCTURE.md) 了解结构
3. 按照 [README.md](../README.md) 快速开始运行项目
4. 查看 [API_TEST_CASES.md](./API_TEST_CASES.md) 了解接口

### 开发过程中

1. 查看 [DOCUMENT_PROCESSING_WORKFLOW.md](./DOCUMENT_PROCESSING_WORKFLOW.md) 了解文档处理
2. 查看 [APACHE_TIKA_INTEGRATION.md](./APACHE_TIKA_INTEGRATION.md) 了解文档解析
3. 运行 [TESTING_GUIDE.md](./TESTING_GUIDE.md) 中的测试

### 部署前

1. 阅读 `deployment/` 目录下的部署文档
2. 查看部署脚本：`deployment/deploy.sh`
3. 准备生产环境配置

---

## 📧 反馈和问题

如有问题或建议，请：
1. 查看相关文档
2. 检查 [TESTING_GUIDE.md](./TESTING_GUIDE.md) 故障排查部分
3. 联系开发团队
