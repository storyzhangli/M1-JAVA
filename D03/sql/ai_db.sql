-- 1. 用户表
CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码',
    nickname VARCHAR(50) COMMENT '昵称',
    phone VARCHAR(20) COMMENT '手机号',
    status TINYINT DEFAULT 1 COMMENT '状态 1启用 0禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 2. 角色表
CREATE TABLE sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
    role_code VARCHAR(50) NOT NULL COMMENT '角色编码',
    remark VARCHAR(200) COMMENT '角色描述',
    status TINYINT DEFAULT 1 COMMENT '状态 1启用 0禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

-- 3. 权限表
CREATE TABLE sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    perm_name VARCHAR(50) NOT NULL COMMENT '权限名称',
    perm_code VARCHAR(100) NOT NULL COMMENT '权限标识',
    perm_type TINYINT DEFAULT 1 COMMENT '类型 1菜单 2按钮',
    parent_id BIGINT DEFAULT 0 COMMENT '父级ID',
    url VARCHAR(200) COMMENT '接口路径',
    remark VARCHAR(200) COMMENT '备注',
    UNIQUE KEY uk_perm_code (perm_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统权限表';

-- 4. 用户-角色关联表
CREATE TABLE sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    KEY idx_user_id (user_id),
    KEY idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 5. 角色-权限关联表
CREATE TABLE sys_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    KEY idx_role_id (role_id),
    KEY idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- 初始化测试数据
INSERT INTO sys_user (username, password, nickname) VALUES ('admin', '123456', '管理员');
INSERT INTO sys_role (role_name, role_code) VALUES ('超级管理员', 'admin');
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);
INSERT INTO sys_permission (perm_name, perm_code, url) VALUES ('用户查询', 'user:list', '/user/*');
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (1, 1);
-- D03
-- ========== 已有索引保留 ==========
-- sys_user: uk_username(username)
-- sys_role: uk_role_code(role_code)
-- sys_user_role: idx_user_id(user_id)、idx_role_id(role_id)
-- sys_role_permission: idx_role_id(role_id)、idx_permission_id(permission_id)

-- ========== 新增业务索引 ==========
-- 1. 用户表：按状态查询用户列表（后台管理高频）
CREATE INDEX idx_user_status ON sys_user(status);

-- 2. 权限表：按父级ID查询子权限（菜单树高频）
CREATE INDEX idx_perm_parent ON sys_permission(parent_id);

-- 3. 权限表：按类型查询权限（菜单/按钮分离高频）
CREATE INDEX idx_perm_type ON sys_permission(perm_type);

-- 4. 用户角色关联：用户ID+角色ID组合查询（权限校验高频）
CREATE INDEX idx_user_role_combo ON sys_user_role(user_id, role_id);

