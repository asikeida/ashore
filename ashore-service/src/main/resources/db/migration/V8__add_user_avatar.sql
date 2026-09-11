-- 用户头像：存上传后的公网 URL（/images/avatar/...，MinIO 公共桶经 Nginx 直出）
-- NULL = 未自定义头像（前端展示用昵称首字占位）
ALTER TABLE `user` ADD COLUMN `avatar` VARCHAR(255) NULL COMMENT '头像 URL；NULL=未设置' AFTER `nickname`;
