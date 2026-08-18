package com.banking.beneficiary.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IFSCValidatorTest {

    private final IFSCValidator validator = new IFSCValidator();

    @ParameterizedTest
    @ValueSource(strings = {"HDFC0001234", "SBIN0012345", "ICIC0ABC123", "PUNB0123456"})
    void validIFSCCodes_shouldPassValidation(String ifsc) {
        assertThat(validator.isValid(ifsc, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "hdfc0001234",   // lowercase
            "HDFC1001234",   // 5th char must be 0
            "HDC0001234",    // only 3 letters at start
            "HDFC000123",    // too short
            "HDFC00012345",  // too long
            "",              // empty
            "HDFC-0001234"   // contains hyphen
    })
    void invalidIFSCCodes_shouldFailValidation(String ifsc) {
        assertThat(validator.isValid(ifsc, null)).isFalse();
    }
}
