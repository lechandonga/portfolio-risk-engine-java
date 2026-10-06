package com.github.lechandonga.portfolioriskenginejava.error;

/** Base class for all business-level rejections. Never leaks transport/3rd-party details. */
public class BusinessException extends RuntimeException {
    private final ErrorCode code;

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
