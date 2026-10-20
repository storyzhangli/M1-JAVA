package com.zl.d02.common;

import lombok.Data;

/**
 * 统一返回结果体（Response Body）。
 *
 * <p>所有接口统一返回该结构，包含状态码 {@code code}、提示信息 {@code msg}、业务数据 {@code data}，
 * 便于前端统一处理成功与失败场景。</p>
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> {
    /** 状态码：200 成功，其余为各类错误码 */
    private Integer code;
    /** 提示信息 */
    private String msg;
    /** 业务数据 */
    private T data;

    /**
     * 成功返回（携带数据）。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 统一成功结果
     */
    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMsg("success");
        r.setData(data);
        return r;
    }

    /**
     * 成功返回（不携带数据）。
     *
     * @param <T> 数据类型
     * @return 统一成功结果
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 失败返回。
     *
     * @param code 错误码
     * @param msg  错误提示信息
     * @param <T>  数据类型
     * @return 统一失败结果
     */
    public static <T> Result<T> fail(Integer code, String msg) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }
}
