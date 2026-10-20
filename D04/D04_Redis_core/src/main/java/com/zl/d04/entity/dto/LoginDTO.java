package com.zl.d04.entity.dto;

import lombok.Data;

/**
 * 登录请求数据传输对象（Data Transfer Object）。
 * 用于接收前端提交的用户名和密码。
 */
@Data
public class LoginDTO {
    /** 用户名 */
    private String username;
    /** 密码 */
    private String password;
}
