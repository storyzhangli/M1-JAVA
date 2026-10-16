# MySQL 核心知识：InnoDB B+Tree、最左前缀、EXPLAIN 与慢查询优化

Java + AI 工程化导师 · 阶段 1：Java 工程恢复 · 数据库核心专题

这部分不只要会背索引原理，还要掌握一个完整的生产排查流程：发现慢 SQL → 分析执行计划 → 确定问题原因 → 调整索引或 SQL → 实测验证。

下面以 MySQL 8.0/8.4、InnoDB 为主要讲解环境。

## 一、先记住整个知识体系

InnoDB B+Tree 索引

决定数据如何组织、如何定位和读取。

最左前缀原则

决定联合索引的哪些列能够参与有效的索引查找。

EXPLAIN

查看优化器准备如何访问数据，选择了什么索引。

慢查询定位与优化

找到瓶颈、提出假设、执行验证、比较真实指标。

最重要的认知是：创建索引不是优化完成的标志，真正的优化结果需要执行计划、实际执行数据和业务指标共同验证。

## 二、InnoDB 的 B+Tree 索引底层原理

### 2.1 为什么数据库不直接遍历整张表？

假设一张 `orders` 表有几百万条数据，需要根据订单 ID 查询。

没有适用索引时，数据库可能需要从大量数据页中逐步检查记录；有合适的索引时，则可以先通过有序索引定位目标，再读取相应记录。

InnoDB 的普通索引使用 B-Tree 结构。数据库实践中通常称为 B+Tree 索引：索引按键值有序组织，数据记录位于叶子页，树的多路分支结构使定位不必逐条检查全部记录。InnoDB 默认索引页大小为 16 KiB，但实际值可由初始化时的 `innodb_page_size` 决定。

[MySQL Glossary|MySQL术语表](https://dev.mysql.com/doc/refman/8.4/en/glossary.html?utm_source=chatgpt.com)

[InnoDB索引的物理结构|MySQL8.4](https://dev.mysql.com/doc/refman/8.4/en/innodb-physical-structure.html?ff=nopfpls&utm_source=chatgpt.com)



### B+Tree 的简化示意图

根节点：索引键与子页指针

小于 50	50–99	100 及以上。

中间节点

继续按索引键范围定位下一层页面。

叶子页：按键值有序组织的索引记录

键值 10–49	键值 50–99	键值 100–149 

示意图：节点内容、键值范围和树高均为概念性展示，非真实页结构。

与二叉搜索树相比，B+Tree 的多路分支可以让每个索引页关联多个子页，通常能够减少定位数据时需要访问的树层数。

理解它的核心，不是死记“树高几层”，而是理解：

- 有序：可以支持等值查找和范围查找。
- 多路分支：一个节点能够容纳多条索引记录或子页指针。
- 叶子页：承载索引记录，适合连续范围扫描。
- 分页存储：索引查找的成本与页面访问、缓存命中及实际数据分布有关。

### 2.2 InnoDB 的聚簇索引和二级索引

这是索引面试中的必考点。

### 聚簇索引（Clustered Index）

表的数据行存储在聚簇索引的叶子页中。通常由主键充当聚簇索引。

### 二级索引（Secondary Index）

叶子记录包含二级索引列以及对应的主键值。若查询需要索引中没有的其他列，通常还要根据主键回到聚簇索引查找完整记录。

InnoDB 没有明确主键时，会依次考虑合适的非空唯一索引；如果仍不存在，则会生成隐藏的聚簇索引。

[Clustered and Secondary Indexes | 聚簇索引与二级索引](https://dev.mysql.com/doc/refman/8.4/en/innodb-index-types.html?ff=nopfpls&utm_source=chatgpt.com)



举例：

```
CREATE TABLE users (
    id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(100) NOT NULL,
    age INT NOT NULL,
    city VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_name (name)
) ENGINE = InnoDB;
```

查询：

```
SELECT id, name
FROM users
WHERE name = 'Alice';
```

如果使用 `idx_name`，因为所需的 `name` 和 `id` 都可以从二级索引记录获得，所以可能直接完成查询，不需要额外读取聚簇索引。

但如果查询：

```
SELECT id, name, age, city
FROM users
WHERE name = 'Alice';
```

`age` 和 `city` 不在 `idx_name` 中，通常需要先通过二级索引获得匹配记录的主键，再到聚簇索引查完整行。

这个过程就是回表。

工程意义：覆盖索引可以减少回表，但增加索引列也会扩大索引空间、增加写入维护成本。不能为了覆盖每条 SQL，就无限扩大索引。

[Clustered and Secondary Indexes | 聚簇索引与二级索引](https://dev.mysql.com/doc/refman/8.4/en/innodb-index-types.html?ff=nopfpls&utm_source=chatgpt.com)

[Optimizing InnoDB Queries| 优化 InnoDB 查询](https://dev.mysql.com/doc/refman/8.4/en/optimizing-innodb-queries.html?utm_source=chatgpt.com)



## 三、最左前缀原则：联合索引到底如何使用？

### 3.1 先看一个联合索引

假设建立如下索引：

```
CREATE INDEX idx_user
ON users (name, age, city);
```

InnoDB 按联合键的顺序组织索引。可以把它理解为先按 `name` 排序；`name` 相同时，再按 `age` 排序；两者相同时，再按 `city` 排序。

因此，这个索引最直接支持的连续左前缀有：

- `(name)`
- `(name, age)`
- `(name, age, city)`

MySQL 官方文档明确说明，多列索引可以利用其最左侧的连续列组成的前缀进行查找。

[Multiple-Column Indexes|多列索引](https://dev.mysql.com/doc/refman/8.0/en/multiple-column-indexes.html?utm_source=chatgpt.com)



### 3.2 哪些查询能利用哪些索引列？

| 查询条件                                  | 通常可以使用的索引部分     | 说明           |
| ----------------------------------------- | -------------------------- | -------------- |
| `WHERE name = ?`                          | `name`                     | 使用第一列     |
| `WHERE name = ? AND age = ?`              | `name, age`                | 连续前缀       |
| `WHERE name = ? AND age = ? AND city = ?` | 三列                       | 完整索引键条件 |
| `WHERE age = ?`                           | 不具备直接使用左前缀的条件 | 缺少第一列     |
| `WHERE age = ? AND city = ?`              | 不具备直接使用左前缀的条件 | 从第二列开始   |

最后两种查询，在普通的最左前缀查找路径上不能直接用第二列或第二、三列组成连续索引范围。不过，某些 MySQL 版本和数据分布可能适用 Index Skip Scan 等特定优化，因此不能只看 SQL 字面就断言优化器绝不会使用索引；应以实际执行计划为准。

[多列索引](https://dev.mysql.com/doc/refman/8.0/en/multiple-column-indexes.html?utm_source=chatgpt.com)

[10.3 优化与索引](https://dev.mysql.com/doc/refman/8.4/en/optimization-indexes.html?utm_source=chatgpt.com)



### 3.3 一个更接近企业业务的例子

假设订单表常见查询条件是：

```
WHERE tenant_id = ?
  AND status = ?
  AND created_at >= ?
  AND created_at < ?
```

针对这一访问模式，可以考虑：

```
CREATE INDEX idx_orders_tenant_status_created_at
ON orders (tenant_id, status, created_at);
```

它可以先定位租户，再定位状态，然后在对应范围内查找创建时间。

这里的一个常见设计原则是：

常见等值条件在前，范围条件在后，同时兼顾排序、覆盖需求以及其他高频查询模式。

但不要机械套用“区分度最高的列永远放第一位”。联合索引的顺序取决于真实 SQL 工作负载、等值与范围条件、排序要求和数据分布。

### 3.4 范围条件的关键边界

例如索引：

```
CREATE INDEX idx_abc
ON demo (a, b, c, d);
```

查询：

```
SELECT *
FROM demo
WHERE a = 10
  AND b = 20
  AND c >= 30
  AND d = 40;
```

优化器通常能够利用 `a`、`b` 的等值条件和 `c` 的范围条件定位索引范围；在遇到 `c` 的范围条件之后，`d` 通常不能再用于进一步缩小同一个连续的 B-Tree 查找范围。

不过，`d` 仍可能在索引条件下推（Index Condition Pushdown，ICP）、覆盖索引判断或其他优化中发挥作用。“范围列之后的列完全无用”是错误说法。

## 四、SQL 优化实战：优化前后执行计划对比

这是本节最重要的实战部分。

先明确数据边界：当前没有连接到你的 MySQL 实例，也没有执行真实基准测试。下面给出可直接运行的 SQL，以及与索引设计相对应的典型执行计划形态；所有行数、耗时与实际性能变化均留待真实运行验证，不编造实测数据。

### 4.1 场景：查询某租户一天内最近的 50 笔订单

假设订单表目前只有主键，没有能够满足该查询条件与排序的二级索引。

建表：

```
CREATE TABLE orders (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT UNSIGNED NOT NULL,
    status      TINYINT UNSIGNED NOT NULL,
    created_at  DATETIME(3) NOT NULL,
    total_amount DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;
```

目标 SQL：

```
SELECT id, created_at
FROM orders
WHERE tenant_id = 1001
  AND status = 2
  AND created_at >= '2026-10-01 00:00:00'
  AND created_at <  '2026-10-02 00:00:00'
ORDER BY created_at DESC
LIMIT 50;
```

这条 SQL 有四个重要特征：

- `tenant_id`：等值过滤。
- `status`：等值过滤。
- `created_at`：时间范围过滤。
- `ORDER BY created_at DESC LIMIT 50`：需要按时间倒序获取有限数量的记录。

这里使用左闭右开的时间范围，可以准确表达一天的查询区间，避免使用 `DATE(created_at)` 等函数包裹索引列。

### 4.2 优化前：缺少适用的二级索引

执行：

```
EXPLAIN
SELECT id, created_at
FROM orders
WHERE tenant_id = 1001
  AND status = 2
  AND created_at >= '2026-10-01 00:00:00'
  AND created_at <  '2026-10-02 00:00:00'
ORDER BY created_at DESC
LIMIT 50;
```

若优化器选择全表扫描，常见执行计划形态如下。

优化前 · 典型计划示意

| 字段            | 示意值                                    |
| --------------- | ----------------------------------------- |
| `type`          | `ALL`                                     |
| `possible_keys` | `NULL`                                    |
| `key`           | `NULL`                                    |
| `rows`          | 优化器估算的扫描行数，实际值未知          |
| `Extra`         | `Using where; Using filesort`（典型形态） |

注意：这是预期计划形态，不是本机实测输出。实际选择由数据规模、统计信息和优化器决定。

为什么可能出现这种计划？

第一，没有适用的二级索引可直接定位目标租户、状态和时间范围，MySQL 可能需要检查大量记录。

第二，主键按 `id` 组织，而不是按 `created_at` 组织，因此主键顺序通常无法直接满足时间倒序排序。优化器可能需要额外排序，即 `Using filesort`。

`ALL` 表示全表扫描；`Using filesort` 表示需要额外的排序处理，并不等于一定使用磁盘排序。

[10.2.1 Optimizing SELECT Statements | 10.2.1 优化SELECT语句](https://dev.mysql.com/doc/refman/8.4/en/select-optimization.html?utm_source=chatgpt.com)

[10.8.2 EXPLAIN Output Format | 10.8.2 解释输出格式](https://dev.mysql.com/doc/refman/8.4/en/explain-output.html?utm_source=chatgpt.com)





### 4.3 优化后：建立匹配查询模式的联合索引

创建索引：

```
ALTER TABLE orders
ADD INDEX idx_orders_tenant_status_created_at
    (tenant_id, status, created_at);
```

更新统计信息：

```
ANALYZE TABLE orders;
```

再次运行相同的 `EXPLAIN`。

优化后 · 预期计划形态

| 字段            | 示意值                                                       |
| --------------- | ------------------------------------------------------------ |
| `type`          | `range`                                                      |
| `possible_keys` | `idx_orders_tenant_status_created_at`                        |
| `key`           | `idx_orders_tenant_status_created_at`                        |
| `key_len`       | 以实际执行结果为准                                           |
| `rows`          | 优化器估算的索引访问行数，实际值未知                         |
| `Extra`         | 可能出现 `Using index`、`Backward index scan`；以实际计划为准 |

注意：这是与索引设计相匹配的典型示意，不保证每个环境恰好出现这些字段值。

### 4.4 为什么这个索引能帮助优化？

联合索引为：

```
(tenant_id, status, created_at)
```

其查找过程可以概括为：

第一步：匹配租户

通过 `tenant_id = 1001` 定位索引范围。

第二步：匹配状态

在租户对应范围内继续利用 `status = 2`。

第三步：扫描时间范围

利用 `created_at` 的范围条件定位一天内的索引记录。

第四步：倒序获取记录

索引顺序能够匹配这个排序要求时，可反向扫描并在满足条件后停止获取后续记录。

此查询只读取 `id` 和 `created_at`，而 InnoDB 二级索引记录包含主键。因此，该联合索引在这种投影下可以构成覆盖索引，避免为了取得这两列再回到聚簇索引读取整行。

[17.6.2.1 Clustered and Secondary Indexes | 聚簇索引和二级索引](https://dev.mysql.com/doc/refman/8.4/en/innodb-index-types.html?ff=nopfpls&utm_source=chatgpt.com)

[Optimizing InnoDB Queries | 优化 InnoDB 查询](https://dev.mysql.com/doc/refman/8.4/en/optimizing-innodb-queries.html?utm_source=chatgpt.com)



但必须注意：

- 如果实际执行计划依旧使用全表扫描，需要检查表规模、数据分布、统计信息以及索引是否真正可用。
- 如果实际计划出现 `Using filesort`，需要确认排序是否由该索引满足，以及优化器为何选择额外排序。
- 即使 `key` 正确，索引范围过大、缓存命中率不佳或其他系统瓶颈仍可能影响性能。

### 4.5 如何获取真实的优化前后证据？

MySQL 8.0.18 及之后的版本提供 `EXPLAIN ANALYZE`，可以执行查询并输出实际行数、循环次数与耗时等信息。它与普通 `EXPLAIN` 的估算信息不同。

[15.8.2 EXPLAIN Statement | 15.8.2 EXPLAIN语句](https://dev.mysql.com/doc/refman/8.0/en/explain.html?utm_source=chatgpt.com)



执行：

```
EXPLAIN ANALYZE
SELECT id, created_at
FROM orders
WHERE tenant_id = 1001
  AND status = 2
  AND created_at >= '2026-10-01 00:00:00'
  AND created_at <  '2026-10-02 00:00:00'
ORDER BY created_at DESC
LIMIT 50;
```

请在创建索引前、创建索引后分别运行并保存输出，比较：

| 指标         | 需要观察什么                             |
| ------------ | ---------------------------------------- |
| 访问方式     | 是否从大量记录扫描转为索引范围访问       |
| 实际读取行数 | 实际扫描了多少行，而不仅仅是返回多少行   |
| 实际耗时     | 对比相同数据和相近环境下的执行结果       |
| 排序操作     | 是否减少或消除了额外排序                 |
| 扫描循环数   | 多层迭代或联表查询时是否出现大量重复执行 |

`EXPLAIN ANALYZE` 本身会执行查询。运行前应确认它对目标语句和环境是安全的，避免在生产环境随意执行高成本查询。

本例能从索引设计推导出的变化，是查询具备了更合适的索引访问路径和避免回表的可能性。实际扫描行数、耗时下降幅度以及整体性能收益，必须由真实输出证明，不能预先填造数值。

## 五、EXPLAIN 关键字段：如何读懂执行计划？

执行计划不只是看有没有使用索引。它描述了优化器打算如何访问表、选择什么索引，以及估计需要检查多少数据。

[10.8.2 EXPLAIN Output Format | 0.8.2 解释输出格式](https://dev.mysql.com/doc/refman/8.4/en/explain-output.html?utm_source=chatgpt.com)

[15.8.2 EXPLAIN Statement | 15.8.2 EXPLAIN 语句](https://dev.mysql.com/doc/refman/8.4/en/explain.html?utm_source=chatgpt.com)



### 5.1 重要字段对照表

| 字段            | 含义                     | 工程上怎么看                                       |
| --------------- | ------------------------ | -------------------------------------------------- |
| `id`            | 查询中的 SELECT 标识     | 结合子查询、派生表和联表结构分析                   |
| `select_type`   | SELECT 类型              | 区分 `SIMPLE`、`PRIMARY`、`SUBQUERY`、`DERIVED` 等 |
| `table`         | 访问的表或结果对象       | 找到哪个表存在访问瓶颈                             |
| `type`          | 访问方式                 | 判断全表扫描、索引扫描、范围访问或等值查找         |
| `possible_keys` | 候选索引                 | 优化器认为可能相关的索引                           |
| `key`           | 实际选中的索引           | 判断最终用了哪个索引                               |
| `key_len`       | 选用的索引键长度         | 辅助判断联合索引使用了多长的键部分                 |
| `ref`           | 与索引比较的列或常量     | 理解索引查找使用的比较值                           |
| `rows`          | 估计检查的行数           | 与实际读取行数对比，识别估算偏差                   |
| `filtered`      | 预计通过表条件过滤的比例 | 配合 `rows` 估算后续处理的数据量                   |
| `Extra`         | 其他执行信息             | 查找覆盖索引、额外排序、临时表及索引条件下推等信息 |

这些字段及其含义来自 MySQL 官方的 EXPLAIN 输出说明。特别要注意：InnoDB 的 `rows` 通常是估算值，不是实际扫描行数。

[10.8.2 EXPLAIN Output Format | 10.8.2 解释输出格式](https://dev.mysql.com/doc/refman/8.4/en/explain-output.html?utm_source=chatgpt.com)





### 5.2 `type` 常见访问方式

下面按日常调优的理解方式列出常见类型。它们不能脱离查询结构和数据分布被机械地视作绝对性能排名。

| `type`   | 含义                                       | 判断要点                                   |
| -------- | ------------------------------------------ | ------------------------------------------ |
| `system` | 特殊的单行表访问类型                       | 生产查询中不常作为主要关注点               |
| `const`  | 通过唯一键等条件定位至多一行               | 常见于主键或唯一索引等值查找               |
| `eq_ref` | 联表时每组前序记录通过唯一索引最多匹配一行 | 通常是良好的关联访问方式                   |
| `ref`    | 使用非唯一索引或非唯一键前缀进行等值查找   | 需关注匹配行数                             |
| `range`  | 通过索引范围访问                           | 适用于范围、部分 `IN` 条件等               |
| `index`  | 扫描整个索引树                             | 不等于高效的选择性查找，仍可能扫描大量数据 |
| `ALL`    | 全表扫描                                   | 对大型表上的选择性查询尤其值得排查         |

MySQL 对访问类型的具体定义及适用情况见官方文档。

[10.8.2 EXPLAIN Output Format | 10.8.2 解释输出格式](https://dev.mysql.com/doc/refman/8.4/en/explain-output.html?utm_source=chatgpt.com)



特别注意两点。

第一，`ALL` 不代表 SQL 必然很慢。例如，小表的全表扫描可能比索引访问更划算。

第二，`index` 不代表索引使用效果一定很好。它可能扫描整个索引树；若索引树很大或者需要扫描大量记录，也可能成为瓶颈。

### 5.3 `key`、`key_len`、`rows` 如何结合起来分析？

假设执行计划出现以下现象：

```
possible_keys = idx_orders_tenant_status_created_at
key           = idx_orders_tenant_status_created_at
type          = range
rows          = 优化器估算值
```

这只能说明优化器考虑并选择了一个范围访问计划，不能仅凭 `key` 非空就认定 SQL 已经优化到位。

还要继续检查：

- 索引实际使用到了哪些键部分。
- 优化器估算的扫描量是否合理。
- 实际读取行数与最终返回行数相差多少。
- 是否仍然存在大量回表、排序、临时表或重复访问。
- 执行计划是否符合业务实际的数据分布。

`key_len` 是索引键使用长度的线索，不是直接的“使用了几列”的计数器。它受列类型、可空属性和键定义影响，需要结合 `SHOW INDEX` 与实际索引结构分析。

[10.8.2 EXPLAIN Output Format | 10.8.2 解释输出格式](https://dev.mysql.com/doc/refman/8.4/en/explain-output.html?utm_source=chatgpt.com)





### 5.4 `Extra` 中最容易误解的字段

| 字段值                  | 实际含义                                     | 不要误解为                   |
| ----------------------- | -------------------------------------------- | ---------------------------- |
| `Using index`           | 查询可以仅从索引中获取所需列，即覆盖索引访问 | 只要出现，就说明扫描范围很小 |
| `Using index condition` | 使用索引条件下推过滤部分索引记录             | 查询完全不需要读取表数据     |
| `Using filesort`        | 使用额外的排序处理                           | 一定写入磁盘排序             |
| `Using temporary`       | 执行过程中使用临时表                         | 所有场景下都一定是严重问题   |
| `Backward index scan`   | 反向扫描索引以满足相应顺序                   | 索引失效                     |

如果看到 `Using filesort` 或 `Using temporary`，应结合实际查询判断是否需要优化，而不是为了消除某个字段盲目创建索引。

[10.8.2 EXPLAIN Output Format | 10.8.2 解释输出格式](https://dev.mysql.com/doc/refman/8.4/en/explain-output.html?utm_source=chatgpt.com)





## 六、慢查询定位：从日志到可验证的优化

正确的生产调优顺序不是先给每列建立索引，而是先找到真正的高成本查询。

### 6.1 第一步：识别慢 SQL

MySQL 慢查询日志可以记录满足时间阈值等相关条件的语句。具体记录行为还受配置及其他筛选条件影响。

[7.1.8 Server System Variables | 7.1.8 服务器系统变量](https://dev.mysql.com/doc/refman/8.4/en/server-system-variables.html?utm_source=chatgpt.com)

[7.9.1.6 Using Server Logs to Find Causes of Errors in mysqld | 7.9.1.6 使用服务器日志查找mysqld中的错误原因](https://dev.mysql.com/doc/refman/8.4/en/using-log-files.html?utm_source=chatgpt.com)





先检查当前配置：

```
SHOW VARIABLES LIKE 'slow_query_log';
SHOW VARIABLES LIKE 'long_query_time';
SHOW VARIABLES LIKE 'slow_query_log_file';
SHOW VARIABLES LIKE 'log_output';
```

在具备相应权限、且符合环境变更规范时，可以在测试环境开启慢查询日志：

```
SET GLOBAL slow_query_log = ON;
SET GLOBAL long_query_time = 1;
```

这表示将全局慢查询日志开关打开，并将全局时间阈值设置为 1 秒。它并不意味着所有语句只要超过 1 秒就必然被记录，因为还存在其他日志条件和配置影响。

生产环境还应在持久化配置中设置需要的参数，并考虑重启生效、日志空间、敏感 SQL 信息及运维权限等问题。

### 6.2 第二步：从 Performance Schema 找高成本 SQL

Performance Schema 的语句摘要可以按归一化 SQL 聚合相似语句，查看执行次数、累计时间和行数等统计信息。

[29.10 Performance Schema Statement Digests and Sampling | 29.10 性能模式语句摘要与采样](https://dev.mysql.com/doc/refman/8.4/en/performance-schema-statement-digests.html?utm_source=chatgpt.com)





可以在 MySQL 8.x 环境尝试：

```
SELECT
    DIGEST_TEXT,
    COUNT_STAR,
    ROUND(SUM_TIMER_WAIT / 1e12, 2) AS total_seconds,
    ROUND(AVG_TIMER_WAIT / 1e9, 2) AS avg_ms,
    SUM_ROWS_EXAMINED,
    SUM_ROWS_SENT
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME = DATABASE()
ORDER BY SUM_TIMER_WAIT DESC
LIMIT 10;
```

这条 SQL 用于找出当前数据库中累计执行成本较高的 SQL 摘要。

主要字段：

- `COUNT_STAR`：执行次数。
- `total_seconds`：累计执行时间换算为秒。
- `avg_ms`：平均执行时间换算为毫秒。
- `SUM_ROWS_EXAMINED`：累计检查行数。
- `SUM_ROWS_SENT`：累计返回行数。

这些都是你自己数据库执行后才能得到的真实数据，不应拿示例数字替代。如果 Performance Schema 的相关统计未启用、未采集或被重置，结果也可能不完整。

### 6.3 第三步：用执行计划定位根因

把待分析的 SQL 单独取出：

```
EXPLAIN FORMAT=TREE
SELECT ...;
```

若版本支持并且执行该查询是安全的，可以进一步运行：

```
EXPLAIN ANALYZE
SELECT ...;
```

随后按以下顺序排查：

1. 扫描量是否过大？ 检查 `type`、`key`、`rows`，并结合实际行数判断是否存在不必要的全表扫描或宽范围访问。
2. 索引是否匹配？ 检查联合索引顺序、最左前缀、等值与范围条件、排序和覆盖需求。
3. 是否有额外成本？ 查看回表、排序、临时表、嵌套循环和重复扫描。
4. 估算是否可靠？ 若估算与实际扫描量差距明显，检查数据分布与统计信息，必要时执行 `ANALYZE TABLE` 后重新比较。
5. 是否真的改善？ 在可比的环境、参数和数据状态下再次测量耗时、扫描量及执行计划，而不是只凭索引存在与否下结论。

MySQL 官方调优建议也强调，应该结合 EXPLAIN、索引设计、SQL 条件和表统计信息共同诊断。

[10.2.1 Optimizing SELECT Statements  | 10.2.1 优化SELECT语句](https://dev.mysql.com/doc/refman/8.4/en/select-optimization.html?utm_source=chatgpt.com)

[10.5.6 Optimizing InnoDB Queries | 10.5.6 优化InnoDB查询](https://dev.mysql.com/doc/refman/8.4/en/optimizing-innodb-queries.html?utm_source=chatgpt.com)



## 七、常见 SQL 索引失效与优化避坑

下面这些情况，在 Java 后端项目中很常见。它们不一定意味着索引必然完全失效，但都值得检查实际执行计划。

### 7.1 对索引列使用函数

不推荐直接这样写：

```
SELECT id, created_at
FROM orders
WHERE DATE(created_at) = '2026-10-01';
```

更适合普通时间索引的写法：

```
SELECT id, created_at
FROM orders
WHERE created_at >= '2026-10-01 00:00:00'
  AND created_at <  '2026-10-02 00:00:00';
```

原因是，第一种写法对索引列执行了函数，普通索引往往无法直接根据原始 `created_at` 定位出同样的连续范围；第二种写法则可以使用时间列的范围查找。

### 7.2 使用左侧通配符进行模糊查询

```
WHERE name LIKE '%Alice'
```

普通 B-Tree 索引通常不能通过字符串的开头定位这种后缀匹配。

相较之下：

```
WHERE name LIKE 'Alice%'
```

在字符集、排序规则及其他条件允许时，通常可以利用索引范围查找。

如果业务必须支持任意子串搜索，应结合检索需求考虑全文索引、专用搜索系统或其他合理的数据检索方案。

### 7.3 不必要地使用 `SELECT *`

```
SELECT *
FROM users
WHERE name = 'Alice';
```

如果业务只需要：

```
SELECT id, name
FROM users
WHERE name = 'Alice';
```

后者更有机会利用仅包含 `id` 和 `name` 所需信息的覆盖索引，减少多余列读取。

注意：不要为了追求覆盖索引而无条件把所有列加入二级索引，否则可能增加索引空间以及 INSERT、UPDATE、DELETE 的维护成本。

[10.5.6 Optimizing InnoDB Queries | 10.5.6 优化InnoDB查询](https://dev.mysql.com/doc/refman/8.4/en/optimizing-innodb-queries.html?utm_source=chatgpt.com)

[10.3 Optimization and Indexes | 10.3 优化与索引](https://dev.mysql.com/doc/refman/8.4/en/optimization-indexes.html?utm_source=chatgpt.com)





### 7.4 联合索引顺序与查询模式不匹配

建立：

```
CREATE INDEX idx_status_created
ON orders (status, created_at);
```

查询：

```
SELECT id
FROM orders
WHERE created_at >= '2026-10-01 00:00:00';
```

由于缺少最左列 `status` 的筛选条件，这条查询不能依赖该索引的普通左前缀路径直接定位 `created_at` 范围。具体版本可能存在特殊优化，但仍应通过 `EXPLAIN` 验证，而不是假定优化器一定会采用。

### 7.5 其他必须注意的问题

| 问题                    | 排查方向                                                     |
| ----------------------- | ------------------------------------------------------------ |
| JOIN 后行数突然膨胀     | 检查关联条件、关联键数据类型和索引、连接顺序                 |
| LIMIT 配合很大的 OFFSET | 考虑基于唯一、有序键的游标分页                               |
| 数据变更后执行计划变差  | 检查统计信息和数据分布                                       |
| 建了很多单列索引        | 检查实际查询模式，评估是否需要少量匹配业务访问路径的联合索引 |
| 有索引却仍然扫描很多行  | 检查选择性、范围大小、残余过滤条件及实际读取行数             |

记住：索引本身会占用磁盘和缓存空间，也会增加写入与维护开销。索引设计需要在查询收益、存储成本和写入成本之间取得平衡。

[10.2.1 Optimizing SELECT Statements | 10.2.1 优化SELECT语句](https://dev.mysql.com/doc/refman/8.4/en/select-optimization.html?utm_source=chatgpt.com)

[10.5.6 Optimizing InnoDB Queries | 10.5.6 优化InnoDB查询](https://dev.mysql.com/doc/refman/8.4/en/optimizing-innodb-queries.html?utm_source=chatgpt.com)





## 八、阶段 1 随堂考核：检验是否真正理解

## 5 道核心概念题

提交后查看解析

1\. InnoDB 二级索引叶子记录通常保存什么？

A. 仅保存主键

B. 二级索引键以及对应的主键值

C. 必然保存整行所有字段

2\. 联合索引 (a, b, c) 对 WHERE b = 10 能否直接按普通最左前缀查找？

A. 可以，因为 b 建了联合索引

B. 通常不能直接按 b 构成左前缀查找

C. 所有版本都绝不可能利用索引

3\. EXPLAIN 的 rows 字段表示什么？

A. 必然实际扫描的行数

B. 最终返回给应用的行数

C. 优化器估算需要检查的行数

4\. Using filesort 是否意味着一定进行了磁盘排序？

A. 是

B. 不是，它表示额外的排序处理

C. 说明索引完全没有使用

5\. 为何优化前后不能随意写出提升 10 倍等性能结论？

A. 因为索引优化永远没有性能收益

B. 因为必须在可比条件下测量真实执行数据

C. 因为 EXPLAIN 不支持性能优化

### 记忆简语

B+Tree 定位，联合索引看左前缀，EXPLAIN 看访问路径，慢查询靠实测闭环。

本节的真正交付标准不是背下 `ALL`、`range`、`Using filesort` 等字段，而是你能独立完成一条慢 SQL 的定位、索引设计、执行计划分析和优化后验证，并用真实结果说明优化究竟发生了什么。