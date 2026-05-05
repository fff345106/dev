-- 为users表添加头像URL字段
-- Migration: V2
-- Description: 添加用户头像URL字段，用于存储用户头像的访问地址
-- Author: System
-- Date: 2026-05-05

ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500) DEFAULT NULL COMMENT '用户头像URL';
