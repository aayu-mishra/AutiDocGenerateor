package com.boa.hackathon.autodocgen.service;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.nio.file.Path;

@Service
public class S3UploadService {

    private static final String BUCKET_NAME = "autodocgen-bucket"; // replace with your actual S3 bucket
    private static final Region REGION = Region.AP_SOUTH_1;

    private final S3Client s3Client;


    public S3UploadService() {
        this.s3Client = S3Client.builder()
                .region(REGION)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    public String uploadFile(File file, String key) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(key)
                    .contentType("application/zip")
                    .build();

            s3Client.putObject(request, file.toPath());
            return "https://" + BUCKET_NAME + ".s3." + REGION.id() + ".amazonaws.com/" + key;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to S3: " + e.getMessage(), e);
        }
    }
}

