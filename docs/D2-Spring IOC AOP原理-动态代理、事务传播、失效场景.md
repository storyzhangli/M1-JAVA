### ✅ 核心结论
IoC（控制反转）是Spring容器基石，通过DI依赖注入完成对象创建、依赖管理与全生命周期托管；AOP（面向切面编程）基于动态代理实现横切逻辑解耦，是声明式事务、缓存、权限控制的底层支撑。
Spring 6.x（SpringBoot 3.5.16）默认启用CGLIB代理，内置重构版CGLIB字节码库兼容JDK21；事务传播基于**ThreadLocal绑定数据库连接**实现上下文传递，**90%的事务失效本质都是「方法调用未走代理对象」**。

---

## 一、动态代理与AOP底层原理
### 1. 两种动态代理核心对比
AOP的本质是在目标方法执行前后植入横切逻辑，Spring通过动态代理在运行期生成代理对象，对调用者透明。

| 对比维度 | JDK动态代理                                | CGLIB动态代理                                         |
| -------- | ------------------------------------------ | ----------------------------------------------------- |
| 实现原理 | JDK原生`Proxy`类，生成目标接口的实现类代理 | 基于ASM字节码框架，生成目标类的子类，通过重写方法拦截 |
| 前置条件 | 目标类必须实现至少一个接口                 | 无需接口，可代理任意普通类                            |
| 代理限制 | 只能代理接口中定义的方法                   | 无法代理`final`类、`final`方法、私有方法              |
| 性能表现 | 启动快，运行时调用性能一般                 | 启动慢（字节码生成开销），运行时调用性能更高          |
| 底层回调 | `InvocationHandler.invoke()`               | `MethodInterceptor.intercept()`                       |

### 2. Spring 6.x 代理策略与版本变更
1. **默认策略**：SpringBoot 3.x 延续2.x设定，`spring.aop.proxy-target-class=true` 默认开启，**强制全局使用CGLIB代理**，即使目标类实现了接口也不会降级为JDK代理。
2. **内置CGLIB**：Spring 6.0 起将CGLIB直接内置在`spring-core`模块中，不再依赖独立`cglib.jar`，升级至ASM 9.7+，完美兼容JDK21虚拟线程、密封类语法。
3. **代理限制**：JDK21的`Record`、密封类（`sealed class`）均为final类型，**无法被CGLIB生成子类代理**，此类上的AOP注解（如@Transactional）会静默失效。

### 3. AOP核心执行流程
1. 容器启动时，切面扫描器识别所有`@Aspect`切面类，解析切点表达式与通知类型
2. Bean实例化后，`AnnotationAwareAspectJAutoProxyCreator`判断Bean是否需要代理
3. 需要代理则生成代理对象，替换原始Bean放入IoC容器
4. 调用目标方法时，代理对象按通知优先级依次执行环绕→前置→目标方法→后置/异常→最终通知

> 通知类型优先级（从高到低）：环绕通知 → 前置通知 → 目标方法 → 返回通知 → 异常通知 → 最终通知

---

## 二、事务传播机制
声明式事务是Spring AOP最经典的应用：通过`@Transactional`标记，环绕通知`TransactionInterceptor`拦截方法，自动管理数据库连接的开启、提交、回滚。

### 1. 事务底层原理
- 事务上下文通过`ThreadLocal`存储，同一个线程内共享同一个数据库连接与事务状态
- 嵌套事务时，新事务会挂起旧事务的上下文，执行完成后恢复旧上下文
- SpringBoot 3.x 自动配置事务管理器：引入`spring-boot-starter-jdbc`默认注册`DataSourceTransactionManager`

### 2. 7种传播行为（面试必考3种）
传播行为定义：当一个事务方法被另一个事务方法调用时，事务该如何执行。

| 传播行为             | 存在外层事务时               | 无外层事务时     | 核心特性与适用场景                                         |
| -------------------- | ---------------------------- | ---------------- | ---------------------------------------------------------- |
| **REQUIRED（默认）** | 加入外层事务，合并为一个事务 | 新建一个独立事务 | 最常用；内外同生共死，任意一处回滚全部回滚                 |
| **REQUIRES_NEW**     | 挂起外层事务，新建独立事务   | 新建一个独立事务 | 内外事务完全隔离；内层回滚不影响外层，外层回滚不影响内层   |
| **NESTED**           | 开启嵌套子事务（保存点）     | 新建一个独立事务 | 内层回滚只回滚到保存点，不影响外层；外层回滚则内层一起回滚 |
| SUPPORTS             | 加入外层事务                 | 以非事务方式执行 | 查询类方法，兼容事务与非事务场景                           |
| NOT_SUPPORTED        | 挂起外层事务，以非事务执行   | 以非事务方式执行 | 不需要事务的核心逻辑，避免事务开销                         |
| MANDATORY            | 加入外层事务                 | 抛出异常         | 强制依赖上层事务，防止单独调用                             |
| NEVER                | 抛出异常                     | 以非事务方式执行 | 禁止在事务中运行，保护非事务逻辑                           |

### 3. 核心差异辨析（面试八股）
- **REQUIRED vs REQUIRES_NEW**：前者是合并事务，一损俱损；后者是独立事务，互不干扰。
- **REQUIRES_NEW vs NESTED**：前者是物理上的两个独立事务，后者是逻辑上的嵌套保存点；前者需要独立数据库连接，后者复用同一个连接。

---

## 三、事务失效经典场景（面试高频+工程重灾区）
### 场景1：同类方法互相调用（this调用）
- **原因**：`this`指向当前类的原始对象，不是Spring生成的代理对象，AOP切面无法拦截，事务注解失效。
- **示例**：
  ```java
  @Service
  public class OrderService {
      @Transactional
      public void createOrder() {
          // this调用原始对象方法，addOrderItem的事务完全不生效
          this.addOrderItem();
      }
      @Transactional(propagation = Propagation.REQUIRES_NEW)
      public void addOrderItem() { /* 数据库操作 */ }
  }
  ```
- **解决方案**：
  1. 注入自身Bean，通过注入的代理对象调用
  2. 开启`exposeProxy=true`，使用`AopContext.currentProxy()`获取代理
  3. 业务拆分到不同Service类中

### 场景2：方法非public修饰
- **原因**：Spring AOP默认只拦截`public`方法，非public方法上的`@Transactional`静默失效，不报错也不生效。
- **避坑**：事务方法必须声明为public，内部逻辑抽成private方法，由public事务方法调用。

### 场景3：异常类型不匹配
- **原因**：Spring默认只对`RuntimeException`和`Error`触发回滚，`Exception`下的受检异常（如IOException、SQLException）不会回滚。
- **修复**：`@Transactional(rollbackFor = Exception.class)`，指定全异常回滚。

### 场景4：手动捕获吞掉异常
- **原因**：方法内用try-catch捕获异常且未重新抛出，事务切面收不到异常信号，认为方法正常执行，提交事务。
- **修复**：捕获后手动设置回滚`TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`，或重新抛出异常。

### 场景5：数据库引擎不支持事务
- **原因**：MySQL的MyISAM引擎不支持事务，即使配置正确也不会生效。
- **避坑**：确保表引擎为InnoDB，SpringBoot3.x默认建表引擎为InnoDB。

### 场景6：多线程环境调用
- **原因**：事务上下文绑定在`ThreadLocal`中，子线程与主线程不共享事务上下文，子线程中的事务方法会开启独立事务或无事务。
- **避坑**：不要在多线程中依赖事务传播，分布式场景改用分布式事务。

### 场景7：类未被Spring容器管理
- **原因**：类上没加`@Service`/`@Component`等注解，不在IoC容器中，Spring无法生成代理对象。
- **现象**：注解写了但完全不生效，容器中根本没有对应Bean。

### 场景8：final方法 / final类
- **原因**：CGLIB通过生成子类重写方法实现代理，final方法无法被重写，final类无法被继承，直接导致代理失效。
- **JDK21延伸**：Record、密封类均为final，不能承载事务、AOP逻辑。

---

### ⚠️ 坑点 & 面试高频考点
#### 面试八股必背
1. **Spring AOP的两种代理方式区别？SpringBoot3默认用哪种？**
> 答题要点：JDK基于接口、CGLIB基于子类；SpringBoot3默认`proxy-target-class=true`，全局CGLIB代理。

2. **事务传播REQUIRED、REQUIRES_NEW、NESTED的区别？**
> 答题要点：REQUIRED合并事务，同生共死；REQUIRES_NEW独立事务，互不影响；NESTED嵌套保存点，内层不影响外层，外层影响内层。

3. **@Transactional失效的常见场景有哪些？**
> 答题要点：同类调用、非public、异常不匹配、吞异常、引擎不支持、多线程、类未托管、final方法，核心本质都是「没走代理」或「切面没捕获到回滚信号」。

#### 工程避坑红线
1. 务必统一配置`@Transactional(rollbackFor = Exception.class)`，避免受检异常不回滚。
2. 禁止在同类中直接调用事务方法，必须通过代理对象调用。
3. JDK21的Record、密封类禁止加事务、缓存等AOP注解，必然失效。
4. 使用`AopContext.currentProxy()`必须开启`@EnableAspectJAutoProxy(exposeProxy = true)`，否则抛异常。

### 💻 事务失效修复代码示例
```java
@Service
@EnableAspectJAutoProxy(exposeProxy = true) // 开启代理暴露
public class OrderService {

    @Transactional(rollbackFor = Exception.class)
    public void createOrder() {
        // 方式1：从AopContext获取当前代理对象调用
        ((OrderService) AopContext.currentProxy()).addOrderItem();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void addOrderItem() {
        // 独立事务逻辑
    }
}
```

### 📝 记忆简语
> IoC管对象生命周期，AOP靠代理做横切；JDK接口CGLIB子类，三版默认CGLIB。
> 事务传播七类，默认REQUIRED，独立REQUIRES_NEW，嵌套NESTED保存点。
> 事务失效多因没走代理，同类调用、非公开、吞异常、final是四大坑。

