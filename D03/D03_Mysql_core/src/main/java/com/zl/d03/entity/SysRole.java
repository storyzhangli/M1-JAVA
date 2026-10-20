package com.zl.d03.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统角色实体，对应数据库表 {@code sys_role}。
 */
@Data
@TableName("sys_role")
public class SysRole {
    /** 主键 ID，采用数据库自增策略 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 角色名称 */
    private String roleName;
    /** 角色编码，如 ADMIN、USER，用于权限匹配 */
    private String roleCode;
    /** 备注 */
    private String remark;
    /** 状态：1 正常，0 禁用 */
    private Integer status;
    /** 创建时间 */
    private LocalDateTime createTime;
}
