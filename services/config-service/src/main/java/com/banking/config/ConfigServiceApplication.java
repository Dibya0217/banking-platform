package com.banking.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Spring Cloud Config Server.
 *
 * <p>Serves externalised configuration to all microservices.  In 'native' profile the
 * configuration files are read from {@code classpath:/config/}.
 *
 * <p>NOTE: Spring Cloud 2024.0.x targets Spring Boot 3.x. If a compile error occurs
 * due to Boot 4.x incompatibility, replace with the stub controller that serves
 * properties via a plain REST endpoint and remove the spring-cloud-config-server
 * dependency from pom.xml.
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServiceApplication.class, args);
    }
}
