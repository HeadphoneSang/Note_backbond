-- ============================================================
-- note_backbond 数据库初始化脚本
-- 笔记应用 - 完整建表语句
-- ============================================================

CREATE DATABASE IF NOT EXISTS note_backbond
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE note_backbond;

-- ============================================================
-- 1. 用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS `user` (
    `id`           INT           NOT NULL AUTO_INCREMENT  COMMENT '用户ID',
    `account`      VARCHAR(50)   NOT NULL                 COMMENT '账号（唯一）',
    `password`     VARCHAR(255)  NOT NULL                 COMMENT '密码（加密存储）',
    `nickname`     VARCHAR(50)   DEFAULT NULL             COMMENT '昵称',
    `email`     VARCHAR(50)   DEFAULT NULL             COMMENT '邮箱',
    `phone_number` VARCHAR(20)   DEFAULT NULL             COMMENT '手机号',
    `avatar`       VARCHAR(500)  DEFAULT NULL             COMMENT '头像URL',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_account` (`account`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================================
-- 2. 笔记本表
-- ============================================================
CREATE TABLE IF NOT EXISTS `notebook` (
    `id`          INT           NOT NULL AUTO_INCREMENT  COMMENT '笔记本ID',
    `user_id`     INT           NOT NULL                 COMMENT '所属用户ID',
    `name`        VARCHAR(100)  NOT NULL                 COMMENT '笔记本名称',
    `description` VARCHAR(255)  DEFAULT NULL             COMMENT '描述',
    `color`       VARCHAR(20)   DEFAULT NULL             COMMENT '显示颜色',
    `sort_order`  INT           DEFAULT 0                COMMENT '排序序号',
    `created_at`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    CONSTRAINT `fk_notebook_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记本表';

-- ============================================================
-- 3. 笔记表
-- ============================================================
CREATE TABLE IF NOT EXISTS `note` (
    `id`          INT           NOT NULL AUTO_INCREMENT  COMMENT '笔记ID',
    `user_id`     INT           NOT NULL                 COMMENT '所属用户ID',
    `notebook_id` INT           DEFAULT NULL             COMMENT '所属笔记本ID（可为空）',
    `title`       VARCHAR(200)  DEFAULT 'Untitled'       COMMENT '笔记标题',
    `content`     LONGTEXT      DEFAULT NULL             COMMENT '笔记内容（支持富文本）',
    `summary`     VARCHAR(500)  DEFAULT NULL             COMMENT '笔记摘要',
    `is_marked`   TINYINT(1)    DEFAULT 0                COMMENT '是否星标(0=否 1=是)',
    `is_delete`   TINYINT(1)    DEFAULT 0                COMMENT '是否删除(0=否 1=是)',
    `sort_order`  INT           DEFAULT 0                COMMENT '排序序号',
    `created_at`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted_at`  DATETIME      DEFAULT NULL             COMMENT '删除时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_notebook_id` (`notebook_id`),
    KEY `idx_is_delete` (`is_delete`),
    KEY `idx_is_marked` (`is_marked`),
    CONSTRAINT `fk_note_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_note_notebook` FOREIGN KEY (`notebook_id`) REFERENCES `notebook` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记表';

-- ============================================================
-- 4. 标签表
-- ============================================================
CREATE TABLE IF NOT EXISTS `tag` (
    `id`         INT          NOT NULL AUTO_INCREMENT  COMMENT '标签ID',
    `user_id`    INT          NOT NULL                 COMMENT '所属用户ID',
    `name`       VARCHAR(50)  NOT NULL                 COMMENT '标签名称',
    `color`      VARCHAR(20)  DEFAULT NULL             COMMENT '标签颜色',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_tag` (`user_id`, `name`),
    CONSTRAINT `fk_tag_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签表';

-- ============================================================
-- 5. 笔记-标签关联表
-- ============================================================
CREATE TABLE IF NOT EXISTS `note_tag` (
    `note_id` INT NOT NULL COMMENT '笔记ID',
    `tag_id`  INT NOT NULL COMMENT '标签ID',
    PRIMARY KEY (`note_id`, `tag_id`),
    KEY `idx_tag_id` (`tag_id`),
    CONSTRAINT `fk_nt_note` FOREIGN KEY (`note_id`) REFERENCES `note` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_nt_tag`  FOREIGN KEY (`tag_id`)  REFERENCES `tag` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记-标签关联表';
