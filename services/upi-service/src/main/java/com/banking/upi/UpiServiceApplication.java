package com.banking.upi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.banking.upi", "com.banking.common"})
public class UpiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UpiServiceApplication.class, args);
    }
}
