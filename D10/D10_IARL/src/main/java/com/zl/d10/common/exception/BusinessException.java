package com.zl.d10.common.exception;


import com.zl.d10.common.enums.ResultCodeEnum;
import lombok.Getter;

/**
 * 自定义业务异常
 * 「原理说明」继承RuntimeException，Spring事务默认回滚；携带错误码，对接统一返回体
 * 「坑点提示」必须继承RuntimeException，否则编译期强制捕获，且事务不会自动回滚
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     */
    private final Integer code;

    /**
     * 仅错误信息构造（默认业务错误码）
     */
    public BusinessException(String message) {
        super(message);
        this.code = ResultCodeEnum.BUSINESS_ERROR.getCode();
    }

    /**
     * 错误码+错误信息构造
     */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 通过错误码枚举构造（推荐）
     */
    public BusinessException(ResultCodeEnum resultCodeEnum) {
        super(resultCodeEnum.getMessage());
        this.code = resultCodeEnum.getCode();
    }

    /**
     * 携带原始异常栈构造（便于问题排查）
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = ResultCodeEnum.BUSINESS_ERROR.getCode();
    }
}
