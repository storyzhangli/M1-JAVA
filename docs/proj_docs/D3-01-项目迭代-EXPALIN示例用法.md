~~~
ai_db.sql 如下：
-- 1. 用户表 CREATE TABLE sys_user (     id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',     username VARCHAR(50) NOT NULL COMMENT '用户名',     password VARCHAR(100) NOT NULL COMMENT '密码',     nickname VARCHAR(50) COMMENT '昵称',     phone VARCHAR(20) COMMENT '手机号',     status TINYINT DEFAULT 1 COMMENT '状态 1启用 0禁用',     create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',     update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',     UNIQUE KEY uk_username (username) ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';  -- 2. 角色表 CREATE TABLE sys_role (     id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',     role_name VARCHAR(50) NOT NULL COMMENT '角色名称',     role_code VARCHAR(50) NOT NULL COMMENT '角色编码',     remark VARCHAR(200) COMMENT '角色描述',     status TINYINT DEFAULT 1 COMMENT '状态 1启用 0禁用',     create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',     UNIQUE KEY uk_role_code (role_code) ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';  -- 3. 权限表 CREATE TABLE sys_permission (     id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',     perm_name VARCHAR(50) NOT NULL COMMENT '权限名称',     perm_code VARCHAR(100) NOT NULL COMMENT '权限标识',     perm_type TINYINT DEFAULT 1 COMMENT '类型 1菜单 2按钮',     parent_id BIGINT DEFAULT 0 COMMENT '父级ID',     url VARCHAR(200) COMMENT '接口路径',     remark VARCHAR(200) COMMENT '备注',     UNIQUE KEY uk_perm_code (perm_code) ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统权限表';  -- 4. 用户-角色关联表 CREATE TABLE sys_user_role (     id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',     user_id BIGINT NOT NULL COMMENT '用户ID',     role_id BIGINT NOT NULL COMMENT '角色ID',     KEY idx_user_id (user_id),     KEY idx_role_id (role_id) ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';  -- 5. 角色-权限关联表 CREATE TABLE sys_role_permission (     id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',     role_id BIGINT NOT NULL COMMENT '角色ID',     permission_id BIGINT NOT NULL COMMENT '权限ID',     KEY idx_role_id (role_id),     KEY idx_permission_id (permission_id) ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';  -- 初始化测试数据 INSERT INTO sys_user (username, password, nickname) VALUES ('admin', '123456', '管理员'); INSERT INTO sys_role (role_name, role_code) VALUES ('超级管理员', 'admin'); INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1); INSERT INTO sys_permission (perm_name, perm_code, url) VALUES ('用户查询', 'user:list', '/user/*'); INSERT INTO sys_role_permission (role_id, permission_id) VALUES (1, 1); -- D03 -- ========== 已有索引保留 ========== -- sys_user: uk_username(username) -- sys_role: uk_role_code(role_code) -- sys_user_role: idx_user_id(user_id)、idx_role_id(role_id) -- sys_role_permission: idx_role_id(role_id)、idx_permission_id(permission_id)  -- ========== 新增业务索引 ========== -- 1. 用户表：按状态查询用户列表（后台管理高频） CREATE INDEX idx_user_status ON sys_user(status);  -- 2. 权限表：按父级ID查询子权限（菜单树高频） CREATE INDEX idx_perm_parent ON sys_permission(parent_id);  -- 3. 权限表：按类型查询权限（菜单/按钮分离高频） CREATE INDEX idx_perm_type ON sys_permission(perm_type);  -- 4. 用户角色关联：用户ID+角色ID组合查询（权限校验高频） CREATE INDEX idx_user_role_combo ON sys_user_role(user_id, role_id);  

开启慢查询，sql如下：
-- 开启慢查询，阈值2秒 SET GLOBAL slow_query_log = 'ON'; SET GLOBAL long_query_time = 2; SET GLOBAL log_output = 'TABLE';  -- 查询慢查询记录 SELECT * FROM mysql.slow_log ORDER BY start_time DESC; 

如何索引命中验证
对业务 SQL 执行explain？请举出一个示例
~~~

# 一、索引命中验证方法（官方文档背书）
**验证方式：`EXPLAIN` + 业务SQL**
> 参考官方文档：[https://dev.mysql.com/doc/refman/8.4/en/explain-output.html](https://dev.mysql.com/doc/refman/8.4/en/explain-output.html)
> 原理：`EXPLAIN` 查看 `key`（实际使用索引）、`type`、`key_len`、`rows` 判断索引是否命中；`key_len` 可以判断联合索引命中了多少列。

> 联合索引小规则：`idx_user_role_combo(user_id,role_id)`，遵循最左前缀原则。
> 参考：[https://dev.mysql.com/doc/refman/8.4/en/select-optimization.html](https://dev.mysql.com/doc/refman/8.4/en/select-optimization.html)

## 二、业务场景示例：权限校验高频SQL（核心业务，验证 `idx_user_role_combo`）
业务场景：**根据用户id查询该用户绑定的所有角色**
```sql
EXPLAIN
SELECT r.role_code
FROM sys_user u
INNER JOIN sys_user_role ur ON u.id = ur.user_id
INNER JOIN sys_role r ON ur.role_id = r.id
WHERE u.id = 1 AND u.status = 1;
```
### 预期执行计划解读
1. `sys_user`：主键id查询，`type=const`，主键索引命中
2. `sys_user_role`：`WHERE`传递`user_id=1`，命中**联合索引 `idx_user_role_combo(user_id,role_id)`**
    - `key`字段：`idx_user_role_combo`
    - `type: ref`
3. `sys_role`：主键id查询，`type=const`

---

# 第二个示例：菜单树查询，验证 `idx_perm_parent(parent_id)`
业务场景：查询一级菜单（`parent_id=0`）
```sql
EXPLAIN
SELECT id,perm_name,perm_code,url
FROM sys_permission
WHERE parent_id = 0 AND perm_type =1;
```
- 索引：`idx_perm_parent(parent_id)`
- `key`：`idx_perm_parent`，type=ref，使用索引定位parent_id=0记录，再在Server层过滤perm_type。
> 说明：当前索引只有parent_id单字段，perm_type不在索引内；如果想做到`Using index`覆盖索引，需要新建联合索引`idx_perm_parent_type(parent_id,perm_type,id,perm_name,perm_code,url)`

# 第三个示例：后台用户列表，验证 `idx_user_status(status)`
```sql
EXPLAIN
SELECT id,username,nickname,phone
FROM sys_user
WHERE status = 1;
```
- `key = idx_user_status`，type=range/ref，命中状态索引。

# 第四个示例：联合索引最左前缀反例（验证失效场景）
> `idx_user_role_combo(user_id, role_id)`，跳过最左user_id，仅按role_id查询
```sql
EXPLAIN
SELECT user_id FROM sys_user_role WHERE role_id=1;
```
- `key`：**不会使用联合索引idx_user_role_combo**，只会使用旧单列索引`idx_role_id`
> 符合最左前缀原则：联合索引必须从最左列user_id开始。

# 三、配套慢查询查看SQL（你已经有的）
```sql
-- 查看慢日志
SELECT * FROM mysql.slow_log ORDER BY start_time DESC;
```
> 注意：`SET GLOBAL` 修改全局参数，**新连接生效，当前会话不生效**；需要重新建立数据库连接，慢查询才会采集。
> 官方参考：[https://dev.mysql.com/doc/refman/8.4/en/slow-query-log.html](https://dev.mysql.com/doc/refman/8.4/en/slow-query-log.html)

# 四、常用验证判断清单（EXPLAIN看这4个字段）
1. `key`：不为null，代表命中索引；值就是索引名。
2. `type`：优先 `const > ref > range`；`ALL` = 全表扫描，索引未命中。
3. `key_len`：联合索引时，用来判断**用到索引里多少个字段**。
4. `Extra`：`Using index` = 覆盖索引，无需回表，最优。

# 五、额外建议（针对你的ai_db.sql）
1. `sys_user_role` 已有单列 `idx_user_id`、`idx_role_id`，新增联合索引`idx_user_role_combo(user_id,role_id)`后：
    - 当查询带`user_id`条件，优先走联合索引；
    - 查询只有role_id，依旧走旧单列`idx_role_id`，互不冲突。
2. 不要冗余大量索引；索引会提升查询，降低写入性能。
> 参考：[https://dev.mysql.com/doc/refman/8.4/en/create-index.html](https://dev.mysql.com/doc/refman/8.4/en/create-index.html)

# 层级树
```
索引命中验证体系
├─验证手段：EXPLAIN + 业务SQL
│  ├─核心字段：key、type、key_len、Extra
│  ├─命中判定：key不为NULL，type不是ALL
│  └─联合索引：key_len判断使用了索引的前几列
├─4条演示EXPLAIN示例
│  ├─用户查角色：验证idx_user_role_combo联合索引
│  ├─菜单树查询：验证idx_perm_parent
│  ├─用户列表：验证idx_user_status
│  └─反例：跳过最左列，联合索引失效
├─慢查询采集：slow_query_log
│  └─注意：global参数修改需要新建会话才生效
└─约束：索引加速查询，增加写入开销，避免冗余索引
```

## 记忆简语
验证索引用explain，key不为空代表现；
联合索引看key_len，type全表ALL危险；
慢参global改完重连，索引虽好别泛滥。