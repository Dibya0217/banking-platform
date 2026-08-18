package com.banking.account.service;

import com.banking.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class AccountNumberGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_RETRIES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;

    public String generate() {
        String prefix = LocalDate.now().format(DATE_FORMAT);
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String number = prefix + String.format("%08d", RANDOM.nextInt(100_000_000));
            if (!accountRepository.existsByAccountNumber(number)) {
                return number;
            }
        }
        throw new IllegalStateException("Could not generate a unique account number after " + MAX_RETRIES + " attempts");
    }
}
