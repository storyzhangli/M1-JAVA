package com.zl.d04.controller;

import com.zl.d04.common.Result;
import com.zl.d04.entity.SysUser;
import com.zl.d04.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统用户接口。
 * 缓存读取、防击穿等逻辑统一收敛在 {@link SysUserService}，控制器仅负责参数接收与结果封装。
 */
@Tag(name = "系统用户接口", description = "系统用户查询（带缓存与防击穿）")
@RestController
@RequestMapping("/sys/user")
@RequiredArgsConstructor
public class SysUserController {
    private final SysUserService sysUserService;

    @Operation(summary = "根据ID查询用户", method = "GET", description = "走缓存+分布式锁，返回系统用户信息")
    @GetMapping("/{id}")
    public Result<SysUser> getUserById(@PathVariable Long id) {
        // 缓存命中/未命中、防穿透、防击穿均由 Service 层处理
        SysUser sysUser = sysUserService.getUserById(id);
        if (sysUser == null) {
            return Result.fail(404, "用户不存在");
        }
        return Result.success(sysUser);
    }
}
