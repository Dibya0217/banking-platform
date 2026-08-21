FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY . .
RUN ./mvnw package -pl services/audit-service -am -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S banking && adduser -S banking -G banking
USER banking
WORKDIR /app
COPY --from=builder /build/services/audit-service/target/*.jar app.jar
EXPOSE 8092
ENTRYPOINT ["java", "-jar", "app.jar"]
