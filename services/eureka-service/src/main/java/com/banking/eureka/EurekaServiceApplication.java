package com.banking.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka Service Discovery Server.
 *
 * <p>All microservices register here at startup; the API Gateway and other services
 * resolve logical names (e.g., "auth-service") to host:port pairs via this registry.
 *
 * <p>NOTE: Spring Cloud 2024.0.x targets Spring Boot 3.x. If a compile error
 * occurs due to Boot 4.x incompatibility, replace this with the stub application
 * in the 'stub' source-set and remove the spring-cloud-starter-netflix-eureka-server
 * dependency from pom.xml.
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServiceApplication.class, args);
    }
}
