# 使用轻量级 JRE 镜像运行应用
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 直接复制 Devbox 中已经构建好的 Jar 包
# 注意：你需要确保在 Devbox 中执行了 'mvn clean package' 并且 target 目录下有 jar 包
COPY target/hello-0.0.1-SNAPSHOT.jar app.jar

# 暴露端口
EXPOSE 8080

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
