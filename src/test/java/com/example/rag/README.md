# src/test/java/

这个目录包含所有单元测试和集成测试代码。

## 📁 目录结构

```
src/test/
├── java/
│   └── com/example/rag/
│       ├── service/                    # Service层测试
│       │   ├── DocumentParserServiceTest.java      # Tika文档解析测试
│       │   ├── VectorServiceTest.java              # 向量服务测试
│       │   ├── DocumentServiceTest.java            # 文档服务测试
│       │   └── QAServiceTest.java                  # 问答服务测试
│       │
│       ├── controller/                  # Controller层测试
│       │   ├── AuthControllerTest.java              # 认证接口测试
│       │   └── DocumentControllerTest.java         # 文档接口测试
│       │
│       └── repository/                   # Repository层测试
│           └── DocumentRepositoryTest.java          # 数据访问层测试
│
└── resources/
    ├── application-test.yml             # 测试环境配置
    ├── test-documents/                  # 测试文档
    │   ├── test.txt                     # 纯文本测试
    │   └── sample.txt
    └── schema-test.sql                  # 测试数据库脚本
```

## 🚀 运行测试

### 运行所有测试
```bash
mvn test
```

### 运行特定测试类
```bash
mvn test -Dtest=DocumentParserServiceTest
```

### 运行特定方法
```bash
mvn test -Dtest=DocumentParserServiceTest#testParseTextFile
```

### 生成测试报告
```bash
mvn test jacoco:report
```

## 📝 测试规范

### 命名规范
- 测试类：`[类名]Test.java`
- 测试方法：`test[方法名][场景].java`

### 测试结构
```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {

    @Mock
    private MyRepository myRepository;

    @InjectMocks
    private MyService myService;

    @Test
    void testMethod_Success() {
        // given: 准备测试数据
        // when: 执行被测方法
        // then: 验证结果
    }
}
```

## ⚙️ 测试配置

测试环境配置：`src/test/resources/application-test.yml`

配置说明：
- 使用内存数据库 H2
- 禁用安全配置
- 配置测试日志级别
