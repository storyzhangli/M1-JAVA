-- 开启慢查询，阈值2秒
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 2;
SET GLOBAL log_output = 'TABLE';

-- 查询慢查询记录
SELECT * FROM mysql.slow_log ORDER BY start_time DESC;
