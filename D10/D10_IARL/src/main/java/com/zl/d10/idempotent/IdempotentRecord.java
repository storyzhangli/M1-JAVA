package com.zl.d10.idempotent;

import lombok.Data;

/**
 * 幂等记录：以 JSON 形式存入 Redis，键为 {@code idempotent:<幂等键>}。
 *
 * <p>记录一次幂等请求的状态、参数指纹与结果，用于判断重复、并发与参数复用。</p>
 */
@Data
public class IdempotentRecord {

    /** 处理中：首个请求已占位、业务尚未完成。 */
    public static final String PROCESSING = "PROCESSING";
    /** 已完成：业务处理完成，结果已保存。 */
    public static final String COMPLETED = "COMPLETED";

    /** 当前状态：PROCESSING / COMPLETED。 */
    private String status;

    /** 业务参数指纹（MD5），用于检测「同键不同参数」的非法复用。 */
    private String paramsHash;

    /** 已完成的结果 JSON（COMPLETED 时填充）；重复请求直接返回。 */
    private String result;

    /** 记录创建时间（毫秒）。 */
    private long createdAt;

    /** 最近更新时间（毫秒）。 */
    private long updatedAt;
}
