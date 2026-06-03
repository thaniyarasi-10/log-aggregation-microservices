package com.kovanlabs.servicemanagementservice.service;

import java.io.IOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Service
public class S3Service {

    private final S3Client s3Client;
    private final String bucketName;
    private final String region;

    public S3Service(
            S3Client s3Client,
            @Value("${aws.bucket-name}") String bucketName,
            @Value("${aws.region}") String region) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.region = region;
    }

    public String uploadProfileImage(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = "jpg";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
        }

        String uniqueFileName = UUID.randomUUID().toString() + "." + extension;
        String s3Key = "profile-images/" + uniqueFileName;

        log.info("Uploading profile image to S3: bucket={}, key={}, size={}", bucketName, s3Key, file.getSize());

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        String publicUrl = String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, s3Key);
        log.info("Uploaded successfully. Public URL: {}", publicUrl);
        return publicUrl;
    }
}
