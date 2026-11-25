package com.mointer.cloud_health_monitor.service;

import com.mointer.cloud_health_monitor.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SsrfValidatorTest {

    private SsrfValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SsrfValidator();
        validator.setAllowPrivateIps(false);
    }

    @Test
    @DisplayName("Accepts legitimate public internet URLs")
    void validPublicUrls_shouldPass() {
        assertThatCode(() -> validator.validateUrl("https://google.com"))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validateUrl("https://github.com/status"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:8080",
            "http://localhost:3000",
            "http://169.254.169.254/latest/meta-data/",
            "http://10.0.0.1/admin",
            "http://192.168.1.1/router",
            "http://172.16.0.1/",
            "file:///etc/passwd",
            "ftp://ftp.example.com",
            "gopher://evil.com"
    })
    @DisplayName("Rejects private, loopback, cloud metadata, and forbidden schemes")
    void restrictedTargets_shouldThrowValidationException(String dangerousUrl) {
        assertThatThrownBy(() -> validator.validateUrl(dangerousUrl))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Rejects null or blank URL")
    void emptyUrl_shouldThrowValidationException() {
        assertThatThrownBy(() -> validator.validateUrl(""))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validator.validateUrl(null))
                .isInstanceOf(ValidationException.class);
    }
}
