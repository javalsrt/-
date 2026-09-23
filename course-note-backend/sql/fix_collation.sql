-- ============================================================
-- 排序规则一致性修复脚本
-- 目标库：course_note
-- 问题：教务迁移表（teacher/course/class_info/schedule 等）均为 utf8mb4_unicode_ci，
--       而原小程序 user 表为 utf8mb4_0900_ai_ci。
--       导致 SQL JOIN（如 user.real_name = teacher.real_name）时报
--       "Illegal mix of collations" 异常。
-- 修复：将 user 表关键文本列统一为 utf8mb4_unicode_ci。
-- 幂等：ALTER TABLE ... 可重复执行。
-- ============================================================
SET NAMES utf8mb4;

-- 1) 表默认字符集/排序规则
ALTER TABLE `user` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 2) 关键文本列（参与 JOIN / 登录 / 学号匹配）
ALTER TABLE `user` MODIFY COLUMN `real_name`  varchar(50)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `user` MODIFY COLUMN `username`   varchar(50)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `user` MODIFY COLUMN `student_no` varchar(50)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 校验
SELECT TABLE_NAME, TABLE_COLLATION FROM information_schema.tables
WHERE table_schema = 'course_note' AND TABLE_NAME = 'user';
SELECT COLUMN_NAME, COLLATION_NAME FROM information_schema.columns
WHERE table_schema = 'course_note' AND table_name = 'user'
  AND COLUMN_NAME IN ('real_name','username','student_no');