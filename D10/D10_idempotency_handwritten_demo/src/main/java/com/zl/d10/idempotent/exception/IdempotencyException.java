package com.zl.d10.idempotent.exception;

import lombok.Getter;

/**
 * 幂等业务异常，携带 HTTP 状态码与业务码。
 */
@Getter
public class IdempotencyException extends RuntimeException {

    private final int httpStatus;

    private final int code;

    public IdempotencyException(int httpStatus, int code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }
}
