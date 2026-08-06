package org.template.service;

import io.minio.*;
import io.minio.http.Method;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.template.config.MinioConfig;

import java.util.UUID;

@Service
public class MinioService {

    @Autowired
    private MinioConfig minioConfig;

    @Autowired
    private MinioClient minioClient;

    public String uploadFile(MultipartFile file) throws Exception {
        // 检查桶是否存在，不存在创建
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioConfig.getBucket()).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioConfig.getBucket()).build());
        }
        // 生成存储对象的唯一标志名
        String originalFileName = file.getOriginalFilename();
        assert originalFileName != null;
        String fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        String objectName = UUID.randomUUID().toString() + fileExtension;

        // 上传文件
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(minioConfig.getBucket())
                        .object(objectName)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
        );
        return objectName;
    }


    /**
     * 获取文件访问URL
     * @param objectName 文件名
     * @param expiresInSeconds 过期时间
     * @return  URL
     * @throws Exception 获取URL失败
     */
    public String getFileUrl(String objectName, int expiresInSeconds) throws Exception {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(minioConfig.getBucket())
                        .object(objectName)
                        .expiry(expiresInSeconds)
                        .build()
        );
    }

    // 删除文件
    public void deleteFile(String objectName) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(minioConfig.getBucket())
                        .object(objectName)
                        .build()
        );
    }

}
