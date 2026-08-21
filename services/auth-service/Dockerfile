FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY . .
RUN ./mvnw package -pl services/auth-service -am -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S banking && adduser -S banking -G banking
USER banking
WORKDIR /app
COPY --from=builder /build/services/auth-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
