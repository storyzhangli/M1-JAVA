### 核心结论

问题根因：自定义`BusinessException`未对齐仓库全局异常规范，通常表现为**未继承RuntimeException、缺少错误码字段、与全局异常处理器不匹配**三类问题。以下给出完全对齐M1-JAVA仓库规范的业务异常体系实现，包含错误码枚举、异常类定义、全局异常处理器适配，修正后可直接通过编译且与统一返回体无缝对接。

---

## 一、完整修正代码

### 1. 统一错误码枚举

```
package com.m1.java.d04.common.enums;

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
```

### 2. BusinessException 标准实现

```
package com.m1.java.d04.common.exception;

import com.m1.java.d04.common.enums.ResultCodeEnum;
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
```

### 3. 全局异常处理器适配

```
package com.m1.java.d04.common.handler;

import com.m1.java.d04.common.enums.ResultCodeEnum;
import com.m1.java.d04.common.exception.BusinessException;
import com.m1.java.d04.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 「原理说明」@RestControllerAdvice统一拦截Controller层异常，封装为标准统一返回体
 * 「坑点提示」必须确保该类在Spring扫描包路径下，否则异常无法被捕获
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常：{}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 捕获系统级异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常：", e);
        return Result.fail(ResultCodeEnum.SYSTEM_ERROR.getCode(), ResultCodeEnum.SYSTEM_ERROR.getMessage());
    }
}
```

### 4. ServiceImpl 抛出语句修正（两种规范写法）

#### 写法1：枚举传入（推荐，错误码统一管控）

```
throw new BusinessException(ResultCodeEnum.LOCK_INTERRUPTED);
```

#### 写法2：携带异常栈（保留原始中断异常，便于根因排查）

```
throw new BusinessException("用户查询缓存锁中断", e);
```

---

## 二、常见错误根因排查

1. **编译报错：不兼容的类型**
   - 根因：`BusinessException`错误继承了`Exception`（受检异常），方法未声明`throws`
   - 修复：必须继承`RuntimeException`（非受检异常），符合Spring工程开发约定
2. **异常未被全局处理器捕获**
   - 根因：异常类包路径不在`@RestControllerAdvice`扫描范围内
   - 修复：确保包路径在启动类扫描包下，或手动指定`@RestControllerAdvice(basePackages = "com.m1.java.d04")`
3. **事务不回滚**
   - 根因：自定义异常继承`Exception`，Spring默认仅对`RuntimeException`触发事务回滚
   - 修复：统一继承`RuntimeException`，或手动标注`@Transactional(rollbackFor = Exception.class)`

---

## 三、配套统一返回体（Result）

若仓库中暂无标准返回体，补充对齐实现：

```
package com.m1.java.d04.common.result;

import lombok.Data;

@Data
public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("操作成功");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> fail(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
```

---

### 记忆简语

业务异常运行时，错误码枚举统一管；
全局拦截封返回，事务回滚不用烦。
