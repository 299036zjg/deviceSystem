-- =====================================================================
-- 重置演示库：删除并重建 sems 库
-- 用法：mysql -u root -p < backend/scripts/reset-demo.sql
-- 之后删除 backend/data/uploads 目录并重启后端：
--   启动时 createDatabaseIfNotExist 建库 → schema.sql 建表 → demo-data.sql 写入演示数据
-- =====================================================================

DROP DATABASE IF EXISTS sems;
CREATE DATABASE sems DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
