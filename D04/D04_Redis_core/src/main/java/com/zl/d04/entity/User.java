package com.zl.d04.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户实体，对应数据库表 {@code t_user}。
 */
@Data
@TableName("t_user")
public class User {
    /** 主键 ID，采用数据库自增策略 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 用户名 */
    private String username;
    /** 手机号 */
    private String phone;
}
