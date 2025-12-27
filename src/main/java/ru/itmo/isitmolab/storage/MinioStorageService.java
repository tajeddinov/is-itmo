package ru.itmo.isitmolab.storage;

import io.minio.*;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import ru.itmo.isitmolab.exception.StorageUnavailableException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

@ApplicationScoped
public class MinioStorageService {

    private MinioClient client;

    private String endpoint;
    private String bucket;

    // ленивый флаг
    private volatile boolean bucketReady = false;
    private final Object bucketLock = new Object();

    @PostConstruct
    public void init() {
        // Подхватывай как у тебя принято: env / system props / mp-config.
        // Главное: не localhost в docker!
        this.endpoint = get("MINIO_ENDPOINT", "http://localhost:9000");
        String accessKey = get("MINIO_ACCESS_KEY", "minioadmin");
        String secretKey = get("MINIO_SECRET_KEY", "minioadmin");
        this.bucket = get("MINIO_BUCKET", "isitmo-imports");

        // ВАЖНО: тут ничего не трогаем по сети, чтобы CDI-бин не падал при старте.
        this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    private String get(String key, String def) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) v = System.getProperty(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    /**
     * Ленивая проверка/создание bucket с retry.
     */
    private void ensureBucket() {
        if (bucketReady) return;

        synchronized (bucketLock) {
            if (bucketReady) return;

            RuntimeException last = null;
            int attempts = 3;

            for (int i = 1; i <= attempts; i++) {
                try {
                    boolean exists = client.bucketExists(
                            BucketExistsArgs.builder().bucket(bucket).build()
                    );
                    if (!exists) {
                        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    }
                    bucketReady = true;
                    return;
                } catch (Exception e) {
                    last = new RuntimeException(e);
                    // маленькая пауза, чтобы не молотить MinIO
                    try {
                        Thread.sleep(200L * i);
                    } catch (InterruptedException ignored) {
                    }
                }
            }

            throw new StorageUnavailableException("MinIO ensureBucket failed for bucket=" + bucket + " endpoint=" + endpoint,
                    last == null ? null : last.getCause());
        }
    }

    public void putObject(String objectKey, byte[] bytes, String contentType, Map<String, String> userMeta) {
        ensureBucket();
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            PutObjectArgs.Builder b = PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(in, bytes.length, -1)
                    .contentType(contentType);

            if (userMeta != null && !userMeta.isEmpty()) b.userMetadata(userMeta);

            client.putObject(b.build());
        } catch (Exception e) {
            throw new StorageUnavailableException("MinIO putObject failed: key=" + objectKey, e);
        }
    }

    public InputStream getObject(String objectKey) {
        ensureBucket();
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new StorageUnavailableException("MinIO getObject failed: key=" + objectKey, e);
        }
    }

    public void statObject(String objectKey) {
        ensureBucket();
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new StorageUnavailableException("MinIO statObject failed: key=" + objectKey, e);
        }
    }

    public void copyObject(String fromKey, String toKey) {
        ensureBucket();
        try {
            client.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(bucket)
                            .object(toKey)
                            .source(CopySource.builder().bucket(bucket).object(fromKey).build())
                            .build()
            );
        } catch (Exception e) {
            throw new StorageUnavailableException("MinIO copyObject failed: " + fromKey + " -> " + toKey, e);
        }
    }

    public void removeObjectQuietly(String objectKey) {
        try {
            ensureBucket();
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception ignored) {
            // best-effort
        }
    }
}