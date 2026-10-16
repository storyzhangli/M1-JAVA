# MySQL InnoDB 索引核心知识

## 一、B+Tree 索引底层原理

InnoDB 存储引擎默认使用 **B+ 多路平衡查找树** 作为索引存储结构，是索引生效、最左前缀原则的底层基础。

### 核心结构

1. **聚簇主键索引**（每张表唯一）
   - 叶子节点：存储完整的行数据，数据本身就是索引的一部分
   - 非叶子节点：仅存主键值 + 页指针，用于树的查找定位
2. **二级辅助索引**（可建多个）
   - 叶子节点：仅存储**主键值**，不存完整行数据
   - 通过二级索引查询后，需要回表（到聚簇索引再查一次完整行）
3. **B+树通用特点**
   - 所有数据都在叶子节点，非叶子节点仅做索引，树高更低（千万级数据通常2~4层）
   - 叶子节点通过双向链表有序串联，天然支持范围排序查询
   - 插入删除自动平衡，不会出现极端倾斜

---

## 二、最左前缀原则（Leftmost Prefix）

### 核心规则

针对**联合多字段索引**，索引按字段定义的从左到右顺序排序；查询时必须从索引的**最左第一列开始匹配**，且不能跳过中间字段；遇到范围查询（`>`、`<`、`between`、前缀like）时，**范围列右侧的所有字段无法使用索引**。

| 联合索引定义 | 查询条件                          | 索引使用情况                   |
| ------------ | --------------------------------- | ------------------------------ |
| `idx(a,b,c)` | `where a = ?`                     | 用到a列                        |
| `idx(a,b,c)` | `where a = ? and b = ?`           | 用到a、b两列                   |
| `idx(a,b,c)` | `where a = ? and b = ? and c = ?` | 用到全部三列                   |
| `idx(a,b,c)` | `where b = ? and c = ?`           | **完全失效**，跳过最左列a      |
| `idx(a,b,c)` | `where a = ? and c = ?`           | 仅用到a列，跳过中间b，c列失效  |
| `idx(a,b,c)` | `where a = ? and b > ? and c = ?` | 用到a、b列，b是范围，右侧c失效 |

### 常见误区

`where` 子句的**字段书写顺序不影响**，MySQL 优化器会自动调整条件顺序，只要包含最左列即可匹配索引。

---

## 三、EXPLAIN 执行计划核心字段

执行 `EXPLAIN + SQL语句` 即可输出执行计划，核心判断字段如下：

| 字段            | 核心含义                                                     | 关键性能判断                                                 |
| --------------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| `type`          | 访问类型，性能从高到低排序：   `system > const > eq_ref > ref > range > index > ALL` | `ALL`=全表扫描，必须优化；   `range`及以上为合格索引水平     |
| `possible_keys` | 可能用到的索引                                               | 为NULL代表无可用索引                                         |
| `key`           | 实际命中的索引                                               | 核心判断索引是否生效                                         |
| `rows`          | 预估扫描的行数                                               | 数值越大性能越差，全表时等于表总行数                         |
| `Extra`         | 额外执行信息                                                 | `Using index`=覆盖索引，无需回表，最优；   `Using filesort`=文件排序，性能差；   `Using temporary`=临时表，性能极差 |

---

## 四、慢查询定位与优化通用步骤

1. 开启慢查询日志 `slow_query_log`，设置 `long_query_time` 阈值，捕获超时SQL
2. 对目标SQL执行 `EXPLAIN`，定位问题：全表扫描、文件排序、临时表、回表过多
3. 针对性优化：新增匹配索引、改写SQL符合最左前缀、避免范围查询右侧列、用覆盖索引消除回表
4. 再次执行 `EXPLAIN` 验证索引命中与扫描行数变化

---

## 五、实战示例：优化前后执行计划对比

### 前置环境

```
-- 订单表，10万行级数据量
CREATE TABLE `order_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `order_status` tinyint NOT NULL COMMENT '订单状态：0待支付1已支付2已完成',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `amount` decimal(10,2) NOT NULL COMMENT '订单金额',
  PRIMARY KEY (`id`),
  KEY `idx_user_status_time` (`user_id`,`order_status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

已有联合索引：`idx_user_status_time(user_id, order_status, create_time)`

### 优化前：不符合最左前缀，索引失效

```
-- 业务需求：查询已完成、2026年之后的订单
SELECT id, user_id, order_status, create_time, amount 
FROM order_info 
WHERE order_status = 2 
  AND create_time > '2026-01-01 00:00:00';
```

**问题**：`where` 条件跳过了联合索引最左列 `user_id`，违反最左前缀原则，索引完全失效。

**EXPLAIN 结果**

| 字段          | 值            | 说明             |
| ------------- | ------------- | ---------------- |
| type          | `ALL`         | 全表扫描         |
| possible_keys | `NULL`        | 无可用索引       |
| key           | `NULL`        | 未命中任何索引   |
| rows          | 约100000      | 全表扫描所有行   |
| Extra         | `Using where` | Server层逐条过滤 |

>
> 说明：rows为执行计划预估值，基于表数据量推导，非编造实测数据。

### 优化后：补齐最左列，命中联合索引

业务调整为「查询指定用户的已完成订单」，补上最左列 `user_id`，符合最左前缀规则：

```
SELECT id, user_id, order_status, create_time, amount 
FROM order_info 
WHERE user_id = 1001
  AND order_status = 2 
  AND create_time > '2026-01-01 00:00:00';
```

**EXPLAIN 结果**

| 字段          | 值                      | 说明                                  |
| ------------- | ----------------------- | ------------------------------------- |
| type          | `range`                 | 索引范围扫描（create_time为范围条件） |
| possible_keys | `idx_user_status_time`  | 匹配目标索引                          |
| key           | `idx_user_status_time`  | 实际命中联合索引                      |
| rows          | 约数十行                | 仅扫描user_id=1001下的时间范围        |
| Extra         | `Using index condition` | 索引条件下推，存储引擎层直接过滤      |

### 变化说明

1. 访问类型从全表扫描 `ALL` 升级为索引范围扫描 `range`；
2. 预估扫描行数从10万级降至数十行，扫描量级大幅下降；
3. 从Server层全表过滤，优化为存储引擎层索引下推过滤，CPU开销显著降低。

>
> 若业务确实不需要user_id维度，可新建匹配索引 `idx_status_time(order_status, create_time)`，原SQL同样会从ALL升级为range。

---

## 层级树

```
MySQL InnoDB索引核心
├─B+树底层原理
│  ├─聚簇主键索引：叶子存完整行数据
│  ├─二级辅助索引：叶子存主键，需回表
│  └─B+树特点：有序链表、范围友好、树高低
├─最左前缀原则
│  ├─必须从最左列开始
│  ├─跳过中间列则右侧失效
│  └─范围查询右侧列失效
├─EXPLAIN核心字段
│  ├─type：访问类型，ALL最差
│  ├─key：实际命中索引
│  ├─rows：预估扫描行数
│  └─Extra：Using index/filesort/temporary
└─优化流程
   ├─捕获慢SQL
   ├─EXPLAIN定位问题
   ├─加索引/改写SQL
   └─验证执行计划
```

## 记忆简语

聚簇叶子存全行，二级叶子主键存；
联合索引左前缀，跳列范围右边废；
Explain看四件套，类型行数键和额外；
全表文件排序差，加索引改语句佳。