#================================================================
# RAG知识库智能问答系统 - Docker构建文件
# 多阶段构建：编译 + 运行
#================================================================

# 阶段1：构建阶段
FROM maven:3.9.6-eclipse-temurin-21 AS builder

# 设置工作目录
WORKDIR /app

# 复制项目文件
COPY pom.xml .
COPY src ./src

# 编译打包（跳过测试）
RUN mvn clean package -DskipTests

# 阶段2：运行阶段
FROM eclipse-temurin:21-jre-alpine

# 设置工作目录
WORKDIR /app

# 复制编译好的 JAR 文件
COPY --from=builder /app/target/rag-1.0.0.jar ./rag-app.jar

# 设置 JVM 参数
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"

# 暴露端口
EXPOSE 8080

# 启动命令
CMD ["java", "-jar", "rag-app.jar"]
