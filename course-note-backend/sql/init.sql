-- ============================================
-- 课程笔记拍照识别与整理系统 数据库初始化脚本
-- ============================================

CREATE DATABASE IF NOT EXISTS course_note DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE course_note;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    `openid` VARCHAR(64) NOT NULL UNIQUE COMMENT '微信OpenID',
    `unionid` VARCHAR(64) DEFAULT NULL COMMENT '微信UnionID',
    `nickname` VARCHAR(64) DEFAULT '' COMMENT '昵称',
    `avatar_url` VARCHAR(512) DEFAULT '' COMMENT '头像URL',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `school` VARCHAR(128) DEFAULT '' COMMENT '学校',
    `major` VARCHAR(128) DEFAULT '' COMMENT '专业',
    `grade` VARCHAR(32) DEFAULT '' COMMENT '年级',
    `semester` VARCHAR(64) DEFAULT '' COMMENT '当前学期',
    `semester_start_date` DATE DEFAULT NULL COMMENT '开学日期',
    `semester_weeks` INT DEFAULT 16 COMMENT '学期周数',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除(0-未删,1-已删)',
    INDEX `idx_openid` (`openid`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 课程表
CREATE TABLE IF NOT EXISTS `course` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '课程ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `name` VARCHAR(128) NOT NULL COMMENT '课程名称',
    `teacher` VARCHAR(64) DEFAULT '' COMMENT '授课教师',
    `classroom` VARCHAR(128) DEFAULT '' COMMENT '上课教室',
    `day_of_week` TINYINT NOT NULL COMMENT '星期几(1-7)',
    `start_time` TIME NOT NULL COMMENT '开始时间',
    `end_time` TIME NOT NULL COMMENT '结束时间',
    `weeks` TEXT COMMENT '上课周次(JSON数组,如[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16])',
    `color` VARCHAR(64) DEFAULT '#4A90D9' COMMENT '课程颜色',
    `semester` VARCHAR(64) DEFAULT '' COMMENT '学期标识,如2024-2025-1',
    `description` VARCHAR(512) DEFAULT '' COMMENT '课程简介',
    `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '状态(0-停用,1-启用)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_semester` (`semester`),
    INDEX `idx_day_of_week` (`day_of_week`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程表';

-- 课段表 (每堂课的具体实例)
CREATE TABLE IF NOT EXISTS `course_session` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '课段ID',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `session_date` DATE NOT NULL COMMENT '上课日期',
    `actual_start_time` DATETIME DEFAULT NULL COMMENT '实际开始时间',
    `actual_end_time` DATETIME DEFAULT NULL COMMENT '实际结束时间',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态(0-未开始,1-进行中,2-已结束)',
    `summary` TEXT DEFAULT NULL COMMENT 'AI生成的课段总结',
    `summary_status` TINYINT NOT NULL DEFAULT 0 COMMENT '总结状态(0-未生成,1-生成中,2-已生成)',
    `material_count` INT NOT NULL DEFAULT 0 COMMENT '资料数量',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX `idx_course_id` (`course_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_session_date` (`session_date`),
    INDEX `idx_course_date` (`course_id`, `session_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课段表';

-- 资料表 (统一存储所有类型的资料)
CREATE TABLE IF NOT EXISTS `material` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '资料ID',
    `session_id` BIGINT NOT NULL COMMENT '所属课段ID',
    `course_id` BIGINT NOT NULL COMMENT '所属课程ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `type` VARCHAR(32) NOT NULL COMMENT '资料类型(PHOTO/NOTE/THIRD_PARTY)',
    `title` VARCHAR(256) DEFAULT '' COMMENT '资料标题',
    `description` TEXT DEFAULT NULL COMMENT '资料描述',
    `content` TEXT DEFAULT NULL COMMENT '文本内容/文字识别结果',
    `file_url` VARCHAR(1024) DEFAULT NULL COMMENT '文件URL',
    `file_size` BIGINT DEFAULT 0 COMMENT '文件大小(字节)',
    `format` VARCHAR(32) DEFAULT NULL COMMENT '文件格式(mp3/mp4/jpg/png/pdf等)',
    `ai_tags` VARCHAR(512) DEFAULT NULL COMMENT 'AI生成的标签(JSON数组)',
    `ai_summary` TEXT DEFAULT NULL COMMENT 'AI生成的内容摘要',
    `ai_processed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'AI是否已处理',
    `source` VARCHAR(64) DEFAULT 'self' COMMENT '来源(自我记录/导入)',
    `sort_order` INT DEFAULT 0 COMMENT '排序序号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_course_id` (`course_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_type` (`type`),
    INDEX `idx_session_type` (`session_id`, `type`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资料表';

-- AI知识库表
CREATE TABLE IF NOT EXISTS `knowledge_base` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '知识库ID',
    `course_id` BIGINT NOT NULL COMMENT '关联课程ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `name` VARCHAR(128) NOT NULL COMMENT '知识库名称',
    `description` VARCHAR(512) DEFAULT '' COMMENT '知识库描述',
    `document_count` INT NOT NULL DEFAULT 0 COMMENT '文档数量',
    `vector_model` VARCHAR(64) DEFAULT 'text-embedding-3-small' COMMENT '向量模型',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX `idx_course_user` (`course_id`, `user_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI知识库表';

-- 知识库文档表
CREATE TABLE IF NOT EXISTS `knowledge_document` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文档ID',
    `knowledge_base_id` BIGINT NOT NULL COMMENT '知识库ID',
    `material_id` BIGINT DEFAULT NULL COMMENT '关联资料ID',
    `user_id` BIGINT NOT NULL DEFAULT 0 COMMENT '上传用户ID(学生隔离)',
    `title` VARCHAR(256) NOT NULL COMMENT '文档标题',
    `content` LONGTEXT NOT NULL COMMENT '文档内容',
    `summary` TEXT DEFAULT NULL COMMENT '文档摘要',
    `file_url` VARCHAR(1024) DEFAULT NULL COMMENT '文件URL',
    `vector_embedding` JSON DEFAULT NULL COMMENT '向量嵌入(JSON存储)',
    `index_status` TINYINT NOT NULL DEFAULT 0 COMMENT '索引状态(0-待索引,1-索引中,2-已索引,3-索引失败)',
    `chunk_count` INT DEFAULT 0 COMMENT '分块数量',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX `idx_knowledge_base_id` (`knowledge_base_id`),
    INDEX `idx_material_id` (`material_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_index_status` (`index_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档表';

-- AI对话记录表
CREATE TABLE IF NOT EXISTS `ai_chat_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `course_id` BIGINT DEFAULT NULL COMMENT '关联课程ID',
    `knowledge_base_id` BIGINT DEFAULT NULL COMMENT '关联知识库ID',
    `session_id` BIGINT DEFAULT NULL COMMENT '关联课段ID',
    `role` VARCHAR(16) NOT NULL COMMENT '角色(user/assistant/system)',
    `content` TEXT NOT NULL COMMENT '对话内容',
    `tokens` INT DEFAULT 0 COMMENT '消耗Tokens数',
    `model` VARCHAR(64) DEFAULT 'deepseek-chat' COMMENT '使用的模型',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_course_id` (`course_id`),
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话记录表';
