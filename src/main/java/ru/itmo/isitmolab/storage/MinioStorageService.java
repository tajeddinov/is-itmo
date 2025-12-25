package ru.itmo.isitmolab.storage;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class MinioStorageService {

    private static final Logger log = Logger.getLogger(MinioStorageService.class.getName());

    private MinioClient client;
    private String bucket;

    @PostConstruct
    public void init() {
        String endpoint = getRequiredEnv("MINIO_ENDPOINT", "http://localhost:9000");
        String accessKey = getRequiredEnv("MINIO_ACCESS_KEY", "minioadmin");
        String secretKey = getRequiredEnv("MINIO_SECRET_KEY", "minioadmin");
        this.bucket = getRequiredEnv("MINIO_BUCKET", "isitmo-imports");

        client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        ensureBucket();
    }

    public String bucket() {
        return bucket;
    }

    public void putObject(String objectKey, byte[] bytes, String contentType, Map<String, String> userMetadata) {
        Objects.requireNonNull(objectKey, "objectKey");
        Objects.requireNonNull(bytes, "bytes");

        String ct = (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;

        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(in, bytes.length, -1)
                    .contentType(ct)
                    .userMetadata(userMetadata == null ? Map.of() : userMetadata)
                    .build();
            client.putObject(args);
        } catch (Exception e) {
            throw new RuntimeException("MinIO putObject failed for key=" + objectKey, e);
        }
    }

    public void copyObject(String fromKey, String toKey) {
        Objects.requireNonNull(fromKey, "fromKey");
        Objects.requireNonNull(toKey, "toKey");
        try {
            client.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(bucket)
                            .object(toKey)
                            .source(CopySource.builder().bucket(bucket).object(fromKey).build())
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("MinIO copyObject failed from " + fromKey + " to " + toKey, e);
        }
    }

    public InputStream getObject(String objectKey) {
        Objects.requireNonNull(objectKey, "objectKey");
        try {
            return client.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (ErrorResponseException ere) {
            if ("NoSuchKey".equalsIgnoreCase(ere.errorResponse().code())) {
                throw new RuntimeException("MinIO object not found: key=" + objectKey, ere);
            }
            throw new RuntimeException("MinIO getObject failed for key=" + objectKey, ere);
        } catch (Exception e) {
            throw new RuntimeException("MinIO getObject failed for key=" + objectKey, e);
        }
    }

    public StatObjectResponse statObject(String objectKey) {
        Objects.requireNonNull(objectKey, "objectKey");
        try {
            return client.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (ErrorResponseException ere) {
            if ("NoSuchKey".equalsIgnoreCase(ere.errorResponse().code())) {
                throw new RuntimeException("MinIO object not found: key=" + objectKey, ere);
            }
            throw new RuntimeException("MinIO statObject failed for key=" + objectKey, ere);
        } catch (Exception e) {
            throw new RuntimeException("MinIO statObject failed for key=" + objectKey, e);
        }
    }

    public void removeObjectQuietly(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return;
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (ErrorResponseException ere) {
            if ("NoSuchKey".equalsIgnoreCase(ere.errorResponse().code())) {
                return;
            }
            log.log(Level.WARNING, "MinIO removeObject failed for key=" + objectKey, ere);
        } catch (Exception e) {
            log.log(Level.WARNING, "MinIO removeObject failed for key=" + objectKey, e);
        }
    }

    private void ensureBucket() {
        try {
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build()
            );
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info(() -> "MinIO bucket created: " + bucket);
            } else {
                log.info(() -> "MinIO bucket exists: " + bucket);
            }
        } catch (Exception e) {
            throw new RuntimeException("MinIO ensureBucket failed for bucket=" + bucket, e);
        }
    }

    private String getRequiredEnv(String key, String defaultValue) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            v = System.getProperty(key);
        }
        if (v == null || v.isBlank()) {
            v = defaultValue;
        }
        return v;
    }
}
