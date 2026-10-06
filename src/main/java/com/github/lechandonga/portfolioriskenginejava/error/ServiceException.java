package com.github.lechandonga.portfolioriskenginejava.error;

/**
 * 业务异常：携带稳定错误码与可解释消息。行情来源等底层异常必须被翻译成本异常
 * （或 {@link ErrorCode} 体系内的结果），不允许直接透传给调用方。
 */
public class ServiceException extends RuntimeException {

    private final ErrorCode code;

    public ServiceException(ErrorCode code) {
        super(code.getDefaultMessage());
        this.code = code;
    }

    public ServiceException(ErrorCode code, String detail) {
        super(code.getDefaultMessage() + "：" + detail);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
