package com.github.lechandonga.portfolioriskenginejava.api;

import java.time.Instant;

public record ErrorResponse(String code, String message, Instant time) {
}
