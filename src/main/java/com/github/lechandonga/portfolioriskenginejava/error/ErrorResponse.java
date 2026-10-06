package com.github.lechandonga.portfolioriskenginejava.error;

import java.time.Instant;

public record ErrorResponse(String code, String message, Instant time) {
    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(code.name(), message, Instant.now());
    }
}
