package com.github.lechandonga.portfolioriskenginejava.api;

import com.github.lechandonga.portfolioriskenginejava.error.ErrorCode;
import com.github.lechandonga.portfolioriskenginejava.error.ServiceException;
import com.github.lechandonga.portfolioriskenginejava.market.SimClock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final SimClock clock;

    public GlobalExceptionHandler(SimClock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ErrorResponse> handleService(ServiceException e) {
        HttpStatus status = httpStatus(e.getCode());
        return ResponseEntity.status(status).body(
                new ErrorResponse(e.getCode().name(), e.getMessage(), clock.now()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                ErrorCode.BAD_REQUEST.name(),
                ErrorCode.BAD_REQUEST.getDefaultMessage() + "：" + e.getMessage(),
                clock.now()));
    }

    /** 兜底：任何未预期异常也不裸抛给调用方。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(
                ErrorCode.MARKET_DATA_UNAVAILABLE.name(),
                "服务内部错误，请联系管理员",
                clock.now()));
    }

    private HttpStatus httpStatus(ErrorCode code) {
        return switch (code) {
            case ACCOUNT_NOT_FOUND, ORDER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SYMBOL_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ORDER_DUPLICATE, CORPORATE_ACTION_DUPLICATE -> HttpStatus.CONFLICT;
            case INSUFFICIENT_CASH, INSUFFICIENT_POSITION, ILLEGAL_AMOUNT, BAD_REQUEST,
                 RISK_INVALID_CONFIDENCE, CORPORATE_ACTION_INVALID,
                 CORPORATE_ACTION_UNKNOWN_TYPE, REPLAY_BAD_TIMESTAMP,
                 REPLAY_UNKNOWN_SYMBOL -> HttpStatus.BAD_REQUEST;
            case ORDER_NOT_CANCELLABLE, CORPORATE_ACTION_OPEN_ORDERS -> HttpStatus.CONFLICT;
            case MARKET_DATA_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case MARKET_DATA_DIRTY, MARKET_DATA_UNAVAILABLE, ORDER_PRICE_UNAVAILABLE,
                 PRICE_STALE, VALUATION_IMPOSSIBLE, RISK_HISTORY_INSUFFICIENT,
                 RISK_PRICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
