package com.cradlenotes.cradlenotes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * BackupController - backs up all notes, media and datasets to S3
 *
 * Endpoints:
 *   POST /api/backup         - run a full backup to S3
 *   GET  /api/backup/status  - show last backup info
 */
@RestController
@CrossOrigin(origins = "*")
public class BackupController {

    @Value("${aws.accessKeyId}")
    private String accessKeyId;

    @Value("${aws.secretKey}")
    private String secretKey;

    @Value("${aws.region}")
    private String region;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    private static final Path NOTES_HOME =
            Path.of(System.getProperty("user.home"), ".notes");

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss");

    // Track last backup info
    private String lastBackupTime = "Never";
    private int lastBackupCount   = 0;
    private String lastBackupStatus = "No backup run yet";

    // -----------------------------------------------
    // POST /api/backup
    // Backs up all files to S3
    // -----------------------------------------------
    @PostMapping("/api/backup")
    public Map<String, Object> runBackup() {
        Map<String, Object> response = new LinkedHashMap<>();

        try {
            // Build S3 client
            S3Client s3 = buildS3Client();

            int fileCount = 0;
            List<String> uploaded = new ArrayList<>();

            // Back up notes, media and datasets
            fileCount += backupDirectory(s3, NOTES_HOME.resolve("notes"),    "notes");
            fileCount += backupDirectory(s3, NOTES_HOME.resolve("media"),     "media");
            fileCount += backupDirectory(s3, NOTES_HOME.resolve("datasets"),  "datasets");

            lastBackupTime   = LocalDateTime.now().format(DISPLAY_FORMAT);
            lastBackupCount  = fileCount;
            lastBackupStatus = "Success";

            response.put("status",    "success");
            response.put("message",   "Backup complete!");
            response.put("fileCount", fileCount);
            response.put("bucket",    bucketName);
            response.put("time",      lastBackupTime);

        } catch (Exception e) {
            lastBackupStatus = "Failed: " + e.getMessage();
            response.put("status",  "error");
            response.put("message", "Backup failed: " + e.getMessage());
        }

        return response;
    }

    // -----------------------------------------------
    // GET /api/backup/status
    // Returns info about the last backup
    // -----------------------------------------------
    @GetMapping("/api/backup/status")
    public Map<String, String> getBackupStatus() {
        Map<String, String> status = new LinkedHashMap<>();
        status.put("lastBackup",  lastBackupTime);
        status.put("fileCount",   String.valueOf(lastBackupCount));
        status.put("status",      lastBackupStatus);
        status.put("bucket",      bucketName);
        status.put("region",      region);
        return status;
    }

    // -----------------------------------------------
    // BACKUP DIRECTORY
    // Uploads all files in a directory to S3
    // -----------------------------------------------
    private int backupDirectory(S3Client s3, Path dir, String s3Prefix) throws IOException {
        if (!Files.exists(dir)) return 0;

        int count = 0;
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                // S3 key = prefix/filename
                String key = s3Prefix + "/" + dir.relativize(file).toString();

                PutObjectRequest request = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build();

                s3.putObject(request, file);
                count++;
                System.out.println("Backed up: " + key);
            }
        }
        return count;
    }

    // -----------------------------------------------
    // BUILD S3 CLIENT
    // -----------------------------------------------
    private S3Client buildS3Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretKey);

        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
}
