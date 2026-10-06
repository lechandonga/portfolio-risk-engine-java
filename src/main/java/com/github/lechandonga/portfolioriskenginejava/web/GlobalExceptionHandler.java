package com.github.lechandonga.portfolioriskenginejava.web;

import com.github.lechandonga.portfolioriskenginejava.error.BusinessException;
import com.github.lechandonga.portfolioriskenginejava.error.ErrorCode;
import com.github.lechandonga.portfolioriskenginejava.error.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getCode();
        log.warn("request rejected: code={} message={}", code, ex.getMessage());
        return ResponseEntity
                .status(statusFor(code))
                .body(ErrorResponse.of(code, ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleValidation(Exception ex) {
        String message = ex instanceof MethodArgumentNotValidException manv && manv.getBindingResult().getFieldError() != null
                ? manv.getBindingResult().getFieldError().getField() + ": "
                    + manv.getBindingResult().getFieldError().getDefaultMessage()
                : "malformed or invalid request body";
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, message));
    }

    private HttpStatus statusFor(ErrorCode code) {
        return switch (code) {
            case UNKNOWN_INSTRUMENT, ACCOUNT_NOT_FOUND, ORDER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case VALIDATION_ERROR, INSUFFICIENT_CASH, INSUFFICIENT_QUANTITY,
                 ORDER_ALREADY_FINAL, ORDER_NOT_CANCELLABLE, ORDER_NOT_FILLABLE,
                 QUANTITY_NOT_MULTIPLE_OF_LOT, PRICE_OUT_OF_BAND,
                 CORPORATE_ACTION_DUPLICATE, INACTIVE_INSTRUMENT,
                 CORPORATE_ACTION_UNKNOWN_INSTRUMENT, RISK_NOT_COMPUTABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case MARKET_DATA_UNAVAILABLE, MARKET_DATA_STALE, MARKET_DATA_DIRTY,
                 MARKET_DATA_TIMEOUT, MARKET_SOURCE_FAULT -> HttpStatus.SERVICE_UNAVAILABLE;
            case CONCURRENT_MODIFICATION -> HttpStatus.CONFLICT;
        };
    }
}
