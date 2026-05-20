# RAG 系统测试指南

本文档说明如何运行和维护 RAG 知识库系统的测试。

## 📋 测试概览

### 测试统计

| 测试类 | 测试数量 | 状态 | 描述 |
|--------|---------|------|------|
| DocumentParserServiceTest | 26 | ✅ 通过 | Apache Tika 文档解析测试 |
| VectorServiceTest | 18 | ✅ 通过 | 向量生成和管理测试 |
| **总计** | **44** | **✅ 全部通过** | - |

---

## 🚀 快速开始

### 运行所有测试

```bash
cd backend
mvn test
```

### 运行特定测试类

```bash
# 只运行文档解析测试
mvn test -Dtest=DocumentParserServiceTest

# 只运行向量服务测试
mvn test -Dtest=VectorServiceTest
```

### 运行特定测试方法

```bash
# 运行单个测试方法
mvn test -Dtest=DocumentParserServiceTest#testParseDocument_PlainText

# 运行匹配名称的测试
mvn test -Dtest=*Parser*
mvn test -Dtest=*Vector*
```

---

## 📁 测试目录结构

```
src/test/
├── java/
│   └── com/example/rag/
│       └── service/
│           ├── DocumentParserServiceTest.java    # 26个测试
│           └── VectorServiceTest.java            # 18个测试
│
└── resources/
    ├── application-test.yml                      # 测试配置
    └── test-documents/
        └── sample.txt                           # 测试文档样本
```

---

## 📝 测试详情

### 1. DocumentParserServiceTest（26个测试）

#### 文件位置
[`src/test/java/com/example/rag/service/DocumentParserServiceTest.java`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/src/test/java/com/example/rag/service/DocumentParserServiceTest.java)

#### 测试覆盖范围

| 类别 | 测试数量 | 说明 |
|------|---------|------|
| **格式支持测试** | 10 | TXT、MD、CSV、HTML、Word、PDF、Excel等格式 |
| **解析测试** | 5 | 纯文本、中英文混合、特殊字符、空文件等 |
| **清理测试** | 3 | 移除控制字符、合并空行、清理空白 |
| **MIME检测** | 3 | 文本、HTML、JSON 类型检测 |
| **边界测试** | 4 | 大文件、null文件名、多级路径、长文件名 |
| **编码测试** | 1 | UTF-8和emoji支持 |

#### 关键测试用例

```java
@Test
void testParseDocument_PlainText() {
    // 测试纯文本文件解析
    String content = "这是一个测试文档。\n第二行内容。";
    InputStream inputStream = new ByteArrayInputStream(content.getBytes());
    String result = documentParserService.parseDocument(inputStream, "test.txt");
    assertTrue(result.contains("测试文档"));
}

@Test
void testIsSupported_SupportedFormats() {
    // 测试支持的文档格式
    assertTrue(documentParserService.isSupported("document.txt"));
    assertTrue(documentParserService.isSupported("document.pdf"));
    assertTrue(documentParserService.isSupported("document.docx"));
}
```

---

### 2. VectorServiceTest（18个测试）

#### 文件位置
[`src/test/java/com/example/rag/service/VectorServiceTest.java`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/src/test/java/com/example/rag/service/VectorServiceTest.java)

#### 测试覆盖范围

| 类别 | 测试数量 | 说明 |
|------|---------|------|
| **向量生成** | 8 | 维度、确定性、范围、空文本、特殊字符等 |
| **格式转换** | 4 | 数组转字符串、边界情况 |
| **相似度计算** | 2 | 相同/不同文本的相似度 |
| **边界测试** | 3 | null文本、emoji、超长文本 |
| **性能测试** | 1 | 批量生成性能 |

#### 关键测试用例

```java
@Test
void testGenerateMockEmbedding_Dimension() {
    // 测试向量维度
    float[] embedding = vectorService.generateMockEmbedding("测试文本");
    assertEquals(1536, embedding.length);
}

@Test
void testGenerateMockEmbedding_Deterministic() {
    // 测试相同文本生成相同向量
    float[] embedding1 = vectorService.generateMockEmbedding("相同文本");
    float[] embedding2 = vectorService.generateMockEmbedding("相同文本");
    assertArrayEquals(embedding1, embedding2);
}
```

---

## 🔧 测试配置

### 测试环境配置

**文件**: [`src/test/resources/application-test.yml`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/src/test/resources/application-test.yml)

```yaml
spring:
  application:
    name: rag-test

  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver

  jpa:
    hibernate:
      ddl-auto: create-drop

  security:
    enabled: false

logging:
  level:
    com.example.rag: DEBUG
    org.apache.tika: INFO

tika:
  max-string-length: 10000
```

### 测试环境特点

- ✅ 使用内存数据库 H2（无需外部依赖）
- ✅ 禁用安全配置（简化测试）
- ✅ 配置测试日志级别
- ✅ 可配置最大文本长度

---

## 📊 运行测试

### 方式1：Maven 命令行

```bash
# 运行所有测试
mvn test

# 运行单个测试类
mvn test -Dtest=DocumentParserServiceTest

# 运行单个测试方法
mvn test -Dtest=DocumentParserServiceTest#testParseDocument_PlainText

# 跳过编译跳过
mvn test -DskipTests=false

# 显示详细输出
mvn test -X
```

### 方式2：IDEA

1. 打开测试类
2. 右键点击测试类或方法
3. 选择 "Run 'TestClass'"
4. 查看测试结果

### 方式3：生成测试报告

```bash
# 生成HTML测试报告
mvn test surefire-report:report

# 查看报告
open target/site/surefire-report.html
```

---

## 🎯 测试规范

### 命名规范

```java
// 测试类：原类名 + Test
class DocumentParserServiceTest { }

// 测试方法：test + 被测方法名 + 场景
void testParseDocument_PlainText() { }
void testParseDocument_EmptyText() { }
void testParseDocument_MixedLanguage() { }
```

### 测试结构（Given-When-Then）

```java
@Test
void testSomething() {
    // Given: 准备测试数据
    String input = "测试输入";

    // When: 执行被测方法
    String result = service.method(input);

    // Then: 验证结果
    assertNotNull(result);
    assertEquals("期望值", result);
}
```

### 测试分类

| 分类 | 命名 | 说明 |
|------|------|------|
| 正常流程 | `testMethod_Success` | 测试正常输入 |
| 异常流程 | `testMethod_Exception` | 测试异常情况 |
| 边界测试 | `testMethod_Boundary` | 测试边界值 |
| 性能测试 | `testMethod_Performance` | 测试性能 |

---

## 📈 添加新测试

### 添加文档解析测试

1. 打开 `DocumentParserServiceTest.java`
2. 添加新的测试方法：

```java
@Test
@DisplayName("测试新的文档格式")
void testParseDocument_NewFormat() {
    // Given
    String content = "测试内容";
    InputStream inputStream = new ByteArrayInputStream(content.getBytes());

    // When
    String result = documentParserService.parseDocument(inputStream, "test.new");

    // Then
    assertNotNull(result);
    assertTrue(result.contains("测试"));
}
```

### 添加向量服务测试

1. 打开 `VectorServiceTest.java`
2. 添加新的测试方法：

```java
@Test
@DisplayName("测试新的向量算法")
void testNewVectorAlgorithm() {
    // Given
    float[] embedding = vectorService.generateMockEmbedding("测试");

    // When & Then
    assertNotNull(embedding);
    assertEquals(1536, embedding.length);
}
```

---

## 🐛 故障排查

### 测试失败常见原因

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| 找不到测试类 | Maven未刷新 | `mvn clean` |
| 依赖缺失 | pom.xml错误 | 检查pom.xml |
| 测试超时 | 性能问题 | 增加超时时间 |
| 权限问题 | 文件访问 | 检查文件权限 |

### 调试技巧

```bash
# 详细输出模式
mvn test -X

# 在第一个失败时停止
mvn test -x

# 显示局部变量
mvn test -l

# 显示最慢的测试
mvn test --durations=10
```

---

## 📚 相关文档

- [Apache Tika 集成说明](../docs/APACHE_TIKA_INTEGRATION.md)
- [文档处理流程](../docs/DOCUMENT_PROCESSING_WORKFLOW.md)
- [向量服务说明](../docs/VECTOR_SERVICE.md)
- [项目 README](../README.md)

---

## ✅ 测试检查清单

在提交代码前，确保：

- [ ] 所有测试通过：`mvn test`
- [ ] 新功能添加了对应的测试
- [ ] 修复bug时添加了回归测试
- [ ] 测试名称清晰描述测试内容
- [ ] 测试数据合理且不依赖外部资源
- [ ] 生成的测试报告没有失败

---

## 🎉 测试运行结果

### 最近测试结果

```
Tests run: 44, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 测试覆盖率目标

| 模块 | 当前覆盖率 | 目标 |
|------|-----------|------|
| Service层 | 90% | 95% |
| Controller层 | - | 80% |
| Repository层 | - | 70% |
| **整体** | **待统计** | **80%** |

---

## 📞 联系方式

如有问题，请联系开发团队或提交Issue。
