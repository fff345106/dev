# 使用官方 Maven 镜像进行构建
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# 复制 pom.xml 并预下载依赖
COPY pom.xml .
# 这一步会下载所有依赖，使得下次构建时如果 pom.xml 没有变动，可以利用 Docker 缓存
RUN mvn dependency:go-offline -B

# 复制源代码
COPY src ./src

# 构建项目，跳过测试以加快速度
RUN mvn clean package -DskipTests

# 使用轻量级 JRE 镜像运行
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 从构建阶段复制生成的 jar 包
COPY --from=build /app/target/*.jar app.jar

# 暴露应用端口
EXPOSE 8080

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]
