package com.zl.d10.idempotent.common;

import lombok.Data;

/**
 * 统一响应体。
 *
 * @param <T> 数据类型
 */
@Data
public class ApiResponse<T> {

    /** 业务码：0 成功；其余为错误码。 */
    private int code;

    /** 提示信息。 */
    private String message;

    /** 数据。 */
    private T data;

    public static <T> ApiResponse<T> of(int code, String message, T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setCode(code);
        r.setMessage(message);
        r.setData(data);
        return r;
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return of(0, message, data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return of(code, message, null);
    }
}
