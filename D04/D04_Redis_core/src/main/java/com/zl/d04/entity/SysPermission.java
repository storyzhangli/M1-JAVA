package com.zl.d04.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 系统权限实体，对应数据库表 {@code sys_permission}。
 */
@Data
@TableName("sys_permission")
public class SysPermission {
    /** 主键 ID，采用数据库自增策略 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 权限名称 */
    private String permName;
    /** 权限编码，如 user:add，用于权限匹配 */
    private String permCode;
    /** 权限类型：1 菜单，2 按钮等 */
    private Integer permType;
    /** 父级 ID，用于构建权限树 */
    private Long parentId;
    /** 权限对应的访问 URL */
    private String url;
    /** 备注 */
    private String remark;
}
