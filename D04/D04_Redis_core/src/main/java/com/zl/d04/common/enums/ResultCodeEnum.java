package com.zl.d04.common.enums;

import lombok.Getter;

/**
 * 全局错误码枚举
 * 「规范说明」所有业务异常错误码统一维护，禁止硬编码
 * 「面试考点」错误码分层：1xxx参数错误、2xxx业务错误、3xxx系统错误、4xxx权限错误
 */
@Getter
public enum ResultCodeEnum {
    SUCCESS(200, "操作成功"),
    PARAM_ERROR(1001, "参数错误"),
    BUSINESS_ERROR(2001, "业务处理失败"),
    LOCK_INTERRUPTED(2002, "缓存锁中断，请求失败"),
    SYSTEM_ERROR(3001, "系统异常"),
    NO_PERMISSION(4001, "无权限访问");

    private final Integer code;
    private final String message;

    ResultCodeEnum(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
