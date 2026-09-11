package me.asikeida.ashore.service.image.service.impl;

import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import me.asikeida.ashore.api.exception.ExceptionUtil;
import me.asikeida.ashore.api.resp.StatusEnum;
import me.asikeida.ashore.service.image.service.ImageUploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * MinIO 实现：上传至 ashore-public 桶（公共读），对象名 = {domain}/{日期}/{随机uuid}.{ext}。
 * <p>随机对象名 + 公共读桶的安全前提：对象名不可枚举（UUID），URL 即弱凭证；
 * 同 paicoding/多数社区的图片 URL 形态。公网访问走 Nginx /images/ 反代（静态直出，
 * 不经 Java），应用只负责写入。</p>
 */
@Component
public class MinioImageUploader implements ImageUploader {

    private static final Logger log = LoggerFactory.getLogger(MinioImageUploader.class);

    private static final String BUCKET = "ashore-public";
    private static final String PUBLIC_BASE = "/images/";

    private final io.minio.MinioClient minioClient;

    private final String publicBaseUrl;

    public MinioImageUploader(
            @Value("${minio.endpoint:http://127.0.0.1:9000}") String endpoint,
            @Value("${minio.access-key:}") String accessKey,
            @Value("${minio.secret-key:}") String secretKey,
            @Value("${minio.public-base-url:/images/}") String publicBaseUrl) {
        // 凭据缺失时用占位值构造（MinIOClient 校验非空）——测试上下文无 env 注入也能起容器；
        // 真实调用时签名失败被业务旁路捕获并返回"上传服务不可用"，生产由 ashore.env 注入真实值
        String ak = accessKey == null || accessKey.isBlank() ? "placeholder" : accessKey;
        String sk = secretKey == null || secretKey.isBlank() ? "placeholder" : secretKey;
        this.minioClient = io.minio.MinioClient.builder()
                .endpoint(endpoint)
                .credentials(ak, sk)
                .build();
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl : publicBaseUrl + "/";
    }

    @Override
    public String upload(byte[] data, String ext, String domain) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String objectName = domain + "/" + date + "/"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16) + "." + ext;
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(BUCKET)
                    .object(objectName)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentTypeOf(ext))
                    .build());
            return publicBaseUrl + objectName;
        } catch (Exception e) {
            log.warn("图片上传 MinIO 失败 domain={} ext={}: {}", domain, ext, e.getMessage());
            throw ExceptionUtil.of(StatusEnum.ERROR, "图片上传服务暂不可用，请稍后重试");
        }
    }

    private String contentTypeOf(String ext) {
        return switch (ext.toLowerCase()) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> "application/octet-stream";
        };
    }
}
