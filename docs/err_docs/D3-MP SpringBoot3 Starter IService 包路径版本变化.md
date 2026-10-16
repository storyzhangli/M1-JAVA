# MP SpringBoot3 Starter IService 包路径版本变化（官方文档：[https://baomidou.com/getting-started/install/](https://baomidou.com/getting-started/install/)）

>
> 重点：`com.baomidou.mybatisplus.spring.service.IService` **是 3.5.16 / 3.5.17 新版改动**；旧版本全部是 `com.baomidou.mybatisplus.extension.service.IService`。

## 版本对照表（mybatis-plus-spring-boot3-starter）

| MP版本                                    | IService 正确import                                          | ServiceImpl 实现类包                                         | 说明                                                         |
| ----------------------------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| ≤3.5.15                                   | `import com.baomidou.mybatisplus.extension.service.IService;` | `com.baomidou.mybatisplus.extension.service.impl.ServiceImpl` | 老包结构，extension包同时包含核心service能力，Spring耦合代码放在extension内 |
| 3.5.16、3.5.17（推荐SpringBoot3.5.X使用） | `import com.baomidou.mybatisplus.spring.service.IService;`   | `com.baomidou.mybatisplus.spring.service.impl.ServiceImpl`   | **重构拆分**：Spring相关能力剥离到独立`mybatis-plus-spring`模块；extension包只保留非spring基础能力 |

>
> 来源：MP官方3.5.16 Release Note，苞米豆Gitee发布日志。

## 核心现象与踩坑（你M1项目Dxx模块最容易遇到）

1. **升级到3.5.17后，原来`extension.service.IService`直接报类不存在**
原因：新版把Spring绑定的IService/ServiceImpl迁移进`mybatis-plus-spring`模块，不再放在extension包。
2. 依赖`mybatis-plus-spring-boot3-starter:3.5.17`会**自动传递依赖 mybatis-plus-spring**，直接可以导入 `com.baomidou.mybatisplus.spring.service.IService`，无需额外引入。
3. ❌ 不要两个包混用：不能接口 `spring.service.IService`，实现类 `extension.service.impl.ServiceImpl`，会编译报错。

### 3.5.17 完整示例代码（适配你的RBAC sys_user模块）

```
import com.baomidou.mybatisplus.spring.service.IService;
import com.zl.d02.entity.SysUser;

public interface SysUserService extends IService<SysUser> {
}
```

```
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zl.d02.entity.SysUser;
import com.zl.d02.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {
}
```

## 降级兼容方案（如果想继续使用旧 `extension.service.IService`）

如果不想改大量代码，锁定版本为 `3.5.15`，`mybatis-plus-spring-boot3-starter:3.5.15`，继续使用旧包路径：

```
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
```

## 配套代码生成器适配（M1项目Dxx代码生成）

>
> 如果你使用 mybatis-plus-generator:3.5.17，**生成service代码默认生成新包路径 `com.baomidou.mybatisplus.spring.service.IService`**。
> 如果降级generator版本到3.5.15，则生成旧extension包路径。

## 层级树

```
MP spring-boot3-starter IService包变更
├─版本 <=3.5.15
│  └─IService：com.baomidou.mybatisplus.extension.service.IService
├─版本3.5.16、3.5.17（SpringBoot3.5.X推荐）
│  └─IService：com.baomidou.mybatisplus.spring.service.IService
├─坑点：新旧包不能混用接口和实现类
└─生成器：版本跟随MP版本，自动输出对应import
```

## 记忆简语

MP一六和一七，service包做迁移；
旧版extension，新版spring；
接口实现包一致，混用编译就报错。
