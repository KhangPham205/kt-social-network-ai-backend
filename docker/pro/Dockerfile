# File: docker/dev/Dockerfile
FROM maven:3.9.4-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Skip test để build nhanh
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
# Môi trường Dev có thể thêm công cụ debug nếu cần
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]