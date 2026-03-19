package com.cradlenotes.cradlenotes;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * DatasetsController - REST API for CradleNotes Datasets
 *
 * Datasets are CSV or JSON files stored in ~/.notes/datasets/
 * Each dataset has a sidecar .dataset.yml metadata file.
 *
 * Endpoints:
 *   GET    /api/datasets              - list all datasets
 *   POST   /api/datasets              - upload a new dataset
 *   GET    /api/datasets/{id}         - get dataset metadata
 *   GET    /api/datasets/{id}/preview - preview first 10 rows
 *   DELETE /api/datasets/{id}         - delete dataset and sidecar
 */
@RestController
@CrossOrigin(origins = "*")
public class DatasetsController {

    private static final Path DATASETS_DIR =
            Path.of(System.getProperty("user.home"), ".notes", "datasets");

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final int PREVIEW_ROWS = 10;

    // -----------------------------------------------
    // GET /api/datasets
    // List all datasets with their metadata
    // -----------------------------------------------
    @GetMapping("/api/datasets")
    public List<Map<String, Object>> listDatasets() throws IOException {
        ensureDatasetsDir();
        List<Map<String, Object>> datasets = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(DATASETS_DIR, 1)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> {
                     String name = p.getFileName().toString();
                     return name.endsWith(".csv") || name.endsWith(".json");
                 })
                 .sorted()
                 .forEach(file -> {
                     try {
                         String id = file.getFileName().toString();
                         Path sidecar = DATASETS_DIR.resolve(id + ".dataset.yml");
                         Map<String, Object> meta = new LinkedHashMap<>();
                         meta.put("id", id);

                         if (Files.exists(sidecar)) {
                             Map<String, String> yml = parseYaml(sidecar);
                             meta.put("title",    yml.getOrDefault("title",   id));
                             meta.put("created",  yml.getOrDefault("created", ""));
                             meta.put("modified", yml.getOrDefault("modified",""));
                             meta.put("tags",     yml.getOrDefault("tags",    ""));
                             meta.put("format",   yml.getOrDefault("format",  ""));
                             meta.put("rowCount", yml.getOrDefault("rowCount",""));
                         } else {
                             meta.put("title", id);
                             meta.put("format", id.endsWith(".csv") ? "csv" : "json");
                         }

                         meta.put("size", Files.size(file) + " bytes");
                         datasets.add(meta);
                     } catch (IOException e) {
                         // skip unreadable files
                     }
                 });
        }

        return datasets;
    }

    // -----------------------------------------------
    // POST /api/datasets
    // Upload a CSV or JSON file
    // -----------------------------------------------
    @PostMapping("/api/datasets")
    public Map<String, Object> uploadDataset(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title) throws IOException {

        ensureDatasetsDir();

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new RuntimeException("No file provided");
        }

        String lower = originalName.toLowerCase();
        if (!lower.endsWith(".csv") && !lower.endsWith(".json")) {
            throw new RuntimeException("Only CSV and JSON files are supported");
        }

        // Build unique filename with timestamp
        String ext      = originalName.substring(originalName.lastIndexOf("."));
        String baseName = originalName.substring(0, originalName.lastIndexOf("."));
        String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP);
        String filename = baseName + "-" + timestamp + ext;

        Path filePath = DATASETS_DIR.resolve(filename);
        Files.write(filePath, file.getBytes());

        // Count rows
        String format = ext.equals(".csv") ? "csv" : "json";
        int rowCount = countRows(filePath, format);

        // Create sidecar metadata file
        String now = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String displayTitle = (title != null && !title.isBlank()) ? title : baseName;

        String sidecarContent = String.format("""
                title: %s
                created: %s
                modified: %s
                tags: []
                format: %s
                path: %s
                rowCount: %d
                """, displayTitle, now, now, format, filename, rowCount);

        Path sidecarPath = DATASETS_DIR.resolve(filename + ".dataset.yml");
        Files.writeString(sidecarPath, sidecarContent);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id",       filename);
        response.put("title",    displayTitle);
        response.put("format",   format);
        response.put("rowCount", rowCount);
        response.put("message",  "Dataset uploaded successfully");
        return response;
    }

    // -----------------------------------------------
    // GET /api/datasets/{id}
    // Get dataset metadata
    // -----------------------------------------------
    @GetMapping("/api/datasets/{id}")
    public Map<String, Object> getDataset(@PathVariable String id) throws IOException {
        Path filePath = DATASETS_DIR.resolve(id);
        if (!Files.exists(filePath)) {
            throw new RuntimeException("Dataset not found: " + id);
        }

        Path sidecar = DATASETS_DIR.resolve(id + ".dataset.yml");
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", id);

        if (Files.exists(sidecar)) {
            Map<String, String> yml = parseYaml(sidecar);
            meta.putAll(yml);
        }

        meta.put("size", Files.size(filePath) + " bytes");
        return meta;
    }

    // -----------------------------------------------
    // GET /api/datasets/{id}/preview
    // Returns first N rows as a table (headers + rows)
    // -----------------------------------------------
    @GetMapping("/api/datasets/{id}/preview")
    public Map<String, Object> previewDataset(@PathVariable String id) throws IOException {
        Path filePath = DATASETS_DIR.resolve(id);
        if (!Files.exists(filePath)) {
            throw new RuntimeException("Dataset not found: " + id);
        }

        Map<String, Object> result = new LinkedHashMap<>();

        if (id.toLowerCase().endsWith(".csv")) {
            result = previewCsv(filePath);
        } else if (id.toLowerCase().endsWith(".json")) {
            result = previewJson(filePath);
        }

        return result;
    }

    // -----------------------------------------------
    // DELETE /api/datasets/{id}
    // Deletes dataset file and sidecar
    // -----------------------------------------------
    @DeleteMapping("/api/datasets/{id}")
    public Map<String, String> deleteDataset(@PathVariable String id) throws IOException {
        Path filePath = DATASETS_DIR.resolve(id);
        if (!Files.exists(filePath)) {
            throw new RuntimeException("Dataset not found: " + id);
        }

        Files.delete(filePath);

        Path sidecar = DATASETS_DIR.resolve(id + ".dataset.yml");
        if (Files.exists(sidecar)) Files.delete(sidecar);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Dataset deleted: " + id);
        return response;
    }

    // -----------------------------------------------
    // CSV PREVIEW
    // -----------------------------------------------

    private Map<String, Object> previewCsv(Path filePath) throws IOException {
        List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        Map<String, Object> result = new LinkedHashMap<>();

        if (lines.isEmpty()) {
            result.put("headers", new ArrayList<>());
            result.put("rows", new ArrayList<>());
            return result;
        }

        // First line is headers
        List<String> headers = parseCsvLine(lines.get(0));
        List<List<String>> rows = new ArrayList<>();

        // Up to PREVIEW_ROWS data rows
        int limit = Math.min(lines.size(), PREVIEW_ROWS + 1);
        for (int i = 1; i < limit; i++) {
            rows.add(parseCsvLine(lines.get(i)));
        }

        result.put("headers",   headers);
        result.put("rows",      rows);
        result.put("totalRows", lines.size() - 1);
        result.put("format",    "csv");
        return result;
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString().trim());
        return fields;
    }

    // -----------------------------------------------
    // JSON PREVIEW
    // -----------------------------------------------

    private Map<String, Object> previewJson(Path filePath) throws IOException {
        String content = Files.readString(filePath, StandardCharsets.UTF_8).trim();
        Map<String, Object> result = new LinkedHashMap<>();

        // Raw JSON preview — just return the first chunk
        String preview = content.length() > 2000
                ? content.substring(0, 2000) + "\n... (truncated)"
                : content;

        result.put("format",  "json");
        result.put("preview", preview);
        return result;
    }

    // -----------------------------------------------
    // COUNT ROWS
    // -----------------------------------------------

    private int countRows(Path filePath, String format) {
        try {
            if (format.equals("csv")) {
                long lines = Files.lines(filePath).count();
                return (int) Math.max(0, lines - 1); // subtract header row
            } else {
                // for JSON just count characters as proxy
                return (int) Files.size(filePath);
            }
        } catch (IOException e) {
            return 0;
        }
    }

    // -----------------------------------------------
    // YAML PARSER
    // -----------------------------------------------

    private Map<String, String> parseYaml(Path filePath) {
        Map<String, String> metadata = new LinkedHashMap<>();
        try {
            List<String> lines = Files.readAllLines(filePath);
            for (String line : lines) {
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    metadata.put(parts[0].trim(), parts[1].trim());
                }
            }
        } catch (IOException e) {
            // return empty map
        }
        return metadata;
    }

    // -----------------------------------------------
    // HELPER
    // -----------------------------------------------

    private void ensureDatasetsDir() throws IOException {
        if (!Files.exists(DATASETS_DIR)) Files.createDirectories(DATASETS_DIR);
    }
}
