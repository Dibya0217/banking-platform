package com.banking.upi.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UpiPinEncryptor {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    public String hash(String pin) {
        return encoder.encode(pin);
    }

    public boolean verify(String rawPin, String hashedPin) {
        return encoder.matches(rawPin, hashedPin);
    }
}
