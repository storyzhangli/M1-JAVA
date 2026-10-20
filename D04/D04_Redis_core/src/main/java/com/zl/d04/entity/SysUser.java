package com.zl.d04.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统用户实体，对应数据库表 {@code sys_user}。
 * 用于登录认证，密码字段存储密文。
 */
@Data
@TableName("sys_user")
public class SysUser {
    /** 主键 ID，采用数据库自增策略 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 登录用户名 */
    private String username;
    /** 密码（生产环境存储 BCrypt 密文） */
    private String password;
    /** 昵称 */
    private String nickname;
    /** 手机号 */
    private String phone;
    /** 状态：1 正常，0 禁用 */
    private Integer status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
