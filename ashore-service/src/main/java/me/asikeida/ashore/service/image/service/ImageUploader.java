package me.asikeida.ashore.service.image.service;

/**
 * 图片上传门面（对照 paicoding 的 ImageUploader 接口思想，单实现 MinIO）。
 * <p>上传成功返回可直接访问的公网 URL（经 Nginx /images/ 反代 MinIO 公共桶）。
 * 依赖接口而非厂商 SDK 类型——将来迁云 OSS 时新增实现类，业务零改动。</p>
 */
public interface ImageUploader {

    /**
     * 上传图片。
     *
     * @param data       图片字节（调用方已完成类型/大小校验）
     * @param ext        扩展名（不含点，如 png/jpg/webp；已按魔数或文件名校验过）
     * @param domain     用途域（avatar / post），用作对象名前缀便于管理
     * @return 公网可访问 URL
     */
    String upload(byte[] data, String ext, String domain);
}
