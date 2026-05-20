# tests/

这个目录包含所有 API 测试用例。

## 📁 目录结构

```
tests/
├── __init__.py
├── conftest.py              # pytest 配置和 fixtures
├── config.py                # 测试配置
├── run_tests.py            # 测试运行脚本
├── test_auth.py            # 认证接口测试
├── test_user_management.py # 用户管理接口测试
├── test_knowledge_base.py  # 知识库管理接口测试
├── test_document_management.py  # 文档管理接口测试
├── test_qa.py              # 问答接口测试
└── test_analytics.py       # 运营分析接口测试
```

## 🚀 快速开始

### 1. 安装依赖

```bash
cd backend
pip3 install requests pytest pytest-html
```

### 2. 确保服务运行

```bash
# 启动后端服务
java -jar target/rag-1.0.0.jar
```

### 3. 运行所有测试

```bash
# 方法1: 使用测试脚本
python3 tests/run_tests.py

# 方法2: 直接使用 pytest
pytest tests/ -v

# 方法3: 运行单个测试文件
pytest tests/test_auth.py -v
```

## 📝 测试配置

编辑 `config.py` 修改测试配置：

```python
class Config:
    # API 地址
    BASE_URL = 'http://localhost:8080'
    
    # 测试用户凭证
    ADMIN_USER = {'username': 'admin', 'password': 'admin123'}
    KB_ADMIN_USER = {'username': 'kbadmin', 'password': 'kbadmin123'}
    REGULAR_USER = {'username': 'user', 'password': 'user123'}
```

## 🎯 测试模块

### 按模块运行测试

```bash
# 认证测试
pytest tests/test_auth.py -v

# 用户管理测试
pytest tests/test_user_management.py -v

# 知识库测试
pytest tests/test_knowledge_base.py -v

# 文档管理测试
pytest tests/test_document_management.py -v

# 问答测试
pytest tests/test_qa.py -v

# 运营分析测试
pytest tests/test_analytics.py -v
```

### 按测试函数运行

```bash
# 运行单个测试用例
pytest tests/test_auth.py::TestAuthController::test_login_success -v

# 运行匹配名称的测试
pytest tests/ -k "login" -v
```

## 📊 生成报告

```bash
# 生成 HTML 报告
pytest tests/ --html=reports/test_report.html --self-contained-html

# 生成 JSON 报告
pytest tests/ --json-report --json-report-file=reports/results.json

# 生成覆盖率报告
pip3 install pytest-cov
pytest tests/ --cov=src --cov-report=html
```

## 🔧 调试模式

```bash
# 详细输出
pytest tests/ -vv -s

# 在第一个失败时停止
pytest tests/ -x

# 显示局部变量
pytest tests/ -l

# 显示最慢的测试
pytest tests/ --durations=10
```

## 📝 编写新测试

参考 `test_auth.py` 创建新测试：

```python
import pytest

class TestMyController:
    """控制器测试"""
    
    def test_my_endpoint(self, api_client, admin_token):
        """测试用例描述"""
        # 设置认证
        api_client.set_token(admin_token)
        
        # 发送请求
        response = api_client.get('/api/my/endpoint')
        
        # 断言
        assert response.status_code == 200
        data = response.json()
        assert data['success'] is True
```

## ⚠️ 注意事项

1. **服务必须运行**: 测试需要后端服务在 `http://localhost:8080` 运行
2. **数据库初始化**: 确保数据库已创建并包含默认用户
3. **清理数据**: 某些测试会创建数据，测试之间可能需要清理
4. **并发限制**: 避免同时运行大量测试，可能导致数据库锁

## 🐛 故障排查

### 服务未启动

```bash
# 检查服务状态
curl http://localhost:8080/actuator/health

# 如果未启动，启动服务
cd backend
./mvnw spring-boot:run
# 或
java -jar target/rag-1.0.0.jar
```

### 测试失败

```bash
# 查看详细错误
pytest tests/ -vv -s

# 查看日志
tail -f backend/logs/rag-app.log
```

### 认证问题

```bash
# 测试登录
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

## 📚 相关文档

- [API 测试用例详细文档](./API_TEST_CASES.md) - 完整的测试用例说明
- [API 接口文档](../API_DOCUMENTATION.md) - API 接口详细说明
- [项目 README](../README.md) - 项目整体说明
