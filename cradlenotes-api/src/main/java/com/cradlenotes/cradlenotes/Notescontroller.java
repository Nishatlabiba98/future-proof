package com.cradlenotes.cradlenotes;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * NotesController - REST API for CradleNotes
 *
 * Supports multiple images/media per note.
 */
@RestController
@CrossOrigin(origins = "*")
public class NotesController {

    private static final Path NOTES_DIR =
            Path.of(System.getProperty("user.home"), ".notes", "notes");

    private static final Path MEDIA_DIR =
            Path.of(System.getProperty("user.home"), ".notes", "media");

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    // -----------------------------------------------
    // GET /api/notes
    // -----------------------------------------------
    @GetMapping("/api/notes")
    public List<Map<String, String>> listNotes() throws IOException {
        ensureNotesDir();
        List<Map<String, String>> notes = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(NOTES_DIR, 1)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> {
                     String name = p.getFileName().toString();
                     return name.endsWith(".md")
                         || name.endsWith(".note")
                         || name.endsWith(".txt");
                 })
                 .sorted()
                 .forEach(file -> {
                     Map<String, String> meta = parseYamlHeader(file);
                     meta.put("filename", file.getFileName().toString());
                     notes.add(meta);
                 });
        }
        return notes;
    }

    // -----------------------------------------------
    // GET /api/notes/{filename}
    // -----------------------------------------------
    @GetMapping("/api/notes/{filename}")
    public Map<String, Object> getNote(@PathVariable String filename) throws IOException {
        Path filePath = NOTES_DIR.resolve(filename);

        if (!Files.exists(filePath)) {
            throw new RuntimeException("Note not found: " + filename);
        }

        List<String> lines = Files.readAllLines(filePath);
        Map<String, Object> note = new LinkedHashMap<>();

        // Parse YAML header — collect multiple values per key
        Map<String, List<String>> multiMeta = parseYamlHeaderMulti(filePath);
        for (Map.Entry<String, List<String>> entry : multiMeta.entrySet()) {
            if (entry.getValue().size() == 1) {
                note.put(entry.getKey(), entry.getValue().get(0));
            } else {
                note.put(entry.getKey(), entry.getValue());
            }
        }

        note.put("filename", filename);

        // Find content
        int contentStart = 0;
        int dashCount = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals("---")) {
                dashCount++;
                if (dashCount == 2) { contentStart = i + 1; break; }
            }
        }

        String content = String.join("\n",
                lines.subList(contentStart, lines.size())).trim();
        note.put("content", content);

        return note;
    }

    // -----------------------------------------------
    // POST /api/notes
    // -----------------------------------------------
    @PostMapping("/api/notes")
    public Map<String, String> createNote(@RequestBody Map<String, String> body) throws IOException {
        ensureNotesDir();

        String title   = body.getOrDefault("title",   "Untitled");
        String content = body.getOrDefault("content", "");

        String safeTitle = title.toLowerCase()
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9\\-]", "")
                .replaceAll("-{2,}", "-");

        String filename = safeTitle + ".note";
        Path filePath = NOTES_DIR.resolve(filename);

        String now = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String fileContent = String.format("""
                ---
                title: %s
                created: %s
                modified: %s
                tags: []
                author:
                ---

                %s
                """, title, now, now, content);

        Files.writeString(filePath, fileContent);

        Map<String, String> response = new HashMap<>();
        response.put("filename", filename);
        response.put("title", title);
        response.put("message", "Note created successfully");
        return response;
    }

    // -----------------------------------------------
    // PUT /api/notes/{filename}
    // -----------------------------------------------
    @PutMapping("/api/notes/{filename}")
    public Map<String, String> updateNote(
            @PathVariable String filename,
            @RequestBody Map<String, String> body) throws IOException {

        Path filePath = NOTES_DIR.resolve(filename);
        if (!Files.exists(filePath)) {
            throw new RuntimeException("Note not found: " + filename);
        }

        String newContent = body.getOrDefault("content", "");
        Map<String, List<String>> meta = parseYamlHeaderMulti(filePath);
        String now = LocalDateTime.now().format(TIMESTAMP_FORMAT);

        String fileContent = buildYaml(meta, now) + "\n" + newContent + "\n";
        Files.writeString(filePath, fileContent);

        Map<String, String> response = new HashMap<>();
        response.put("filename", filename);
        response.put("message", "Note updated successfully");
        return response;
    }

    // -----------------------------------------------
    // DELETE /api/notes/{filename}
    // -----------------------------------------------
    @DeleteMapping("/api/notes/{filename}")
    public Map<String, String> deleteNote(@PathVariable String filename) throws IOException {
        Path filePath = NOTES_DIR.resolve(filename);
        if (!Files.exists(filePath)) {
            throw new RuntimeException("Note not found: " + filename);
        }

        Files.delete(filePath);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Note deleted: " + filename);
        return response;
    }

    // -----------------------------------------------
    // POST /api/notes/{filename}/attach
    // Supports multiple media files per note
    // -----------------------------------------------
    @PostMapping("/api/notes/{filename}/attach")
    public Map<String, String> attachMedia(
            @PathVariable String filename,
            @RequestParam("file") MultipartFile file) throws IOException {

        Path notePath = NOTES_DIR.resolve(filename);
        if (!Files.exists(notePath)) {
            throw new RuntimeException("Note not found: " + filename);
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new RuntimeException("No file provided");
        }

        String mediaType = detectMediaType(originalName.toLowerCase());
        if (mediaType == null) {
            throw new RuntimeException("Unsupported file type: " + originalName);
        }

        // Save file with timestamp
        Path mediaDir = MEDIA_DIR.resolve(mediaType);
        if (!Files.exists(mediaDir)) Files.createDirectories(mediaDir);

        String ext      = originalName.substring(originalName.lastIndexOf("."));
        String baseName = originalName.substring(0, originalName.lastIndexOf("."));
        String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP);
        String newName  = baseName + "-" + timestamp + ext;

        Path destFile = mediaDir.resolve(newName);
        Files.write(destFile, file.getBytes());

        // Load existing YAML and ADD new media entry (keep existing ones)
        Map<String, List<String>> meta = parseYamlHeaderMulti(notePath);
        List<String> lines = Files.readAllLines(notePath);

        int contentStart = 0;
        int dashCount = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals("---")) {
                dashCount++;
                if (dashCount == 2) { contentStart = i + 1; break; }
            }
        }

        String content = String.join("\n",
                lines.subList(contentStart, lines.size())).trim();

        // Add new media to existing list
        meta.computeIfAbsent(mediaType, k -> new ArrayList<>())
            .add(destFile.toString());

        String now = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        meta.put("modified", new ArrayList<>(List.of(now)));

        String fileContent = buildYaml(meta, null) + "\n" + content + "\n";
        Files.writeString(notePath, fileContent);

        Map<String, String> response = new HashMap<>();
        response.put("message", "File attached successfully");
        response.put("filename", newName);
        response.put("type", mediaType);
        response.put("url", "/api/media/" + mediaType + "/" + newName);
        return response;
    }

    // -----------------------------------------------
    // GET /api/media/{type}/{filename}
    // -----------------------------------------------
    @GetMapping("/api/media/{type}/{filename}")
    public ResponseEntity<Resource> serveMedia(
            @PathVariable String type,
            @PathVariable String filename) throws IOException {

        Path filePath = MEDIA_DIR.resolve(type).resolve(filename);

        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(filePath);
        String contentType = Files.probeContentType(filePath);
        if (contentType == null) contentType = "application/octet-stream";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    // -----------------------------------------------
    // BUILD YAML STRING from multi-value map
    // -----------------------------------------------
    private String buildYaml(Map<String, List<String>> meta, String newModified) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");

        // Standard fields first
        for (String key : new String[]{"title", "created", "modified", "tags", "author", "status", "priority"}) {
            List<String> values = meta.get(key);
            if (values != null) {
                String val = key.equals("modified") && newModified != null
                        ? newModified : values.get(0);
                sb.append(key).append(": ").append(val).append("\n");
            }
        }

        // Media fields — each entry on its own line (supports multiple)
        for (String key : new String[]{"image", "audio", "video", "document", "link"}) {
            List<String> values = meta.get(key);
            if (values != null) {
                for (String val : values) {
                    sb.append(key).append(": ").append(val).append("\n");
                }
            }
        }

        sb.append("---\n");
        return sb.toString();
    }

    // -----------------------------------------------
    // YAML PARSER - supports multiple values per key
    // -----------------------------------------------
    private Map<String, List<String>> parseYamlHeaderMulti(Path filePath) {
        Map<String, List<String>> metadata = new LinkedHashMap<>();
        try {
            List<String> lines = Files.readAllLines(filePath);

            if (lines.isEmpty() || !lines.get(0).trim().equals("---")) return metadata;

            int yamlEnd = -1;
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).trim().equals("---")) { yamlEnd = i; break; }
            }

            if (yamlEnd == -1) return metadata;

            for (int i = 1; i < yamlEnd; i++) {
                String line = lines.get(i).trim();
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    String key   = parts[0].trim();
                    String value = parts[1].trim();
                    metadata.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
                }
            }

        } catch (IOException e) {
            System.err.println("Could not read: " + filePath.getFileName());
        }
        return metadata;
    }

    // Simple single-value YAML parser for list endpoint
    private Map<String, String> parseYamlHeader(Path filePath) {
        Map<String, String> metadata = new LinkedHashMap<>();
        try {
            Map<String, List<String>> multi = parseYamlHeaderMulti(filePath);
            for (Map.Entry<String, List<String>> entry : multi.entrySet()) {
                metadata.put(entry.getKey(), entry.getValue().get(0));
            }
        } catch (Exception e) {
            metadata.put("title", filePath.getFileName().toString());
        }
        return metadata;
    }

    // -----------------------------------------------
    // DETECT MEDIA TYPE
    // -----------------------------------------------
    private String detectMediaType(String filename) {
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")
                || filename.endsWith(".png") || filename.endsWith(".gif")
                || filename.endsWith(".webp") || filename.endsWith(".heic"))
            return "image";
        if (filename.endsWith(".mp3") || filename.endsWith(".wav")
                || filename.endsWith(".m4a") || filename.endsWith(".aac"))
            return "audio";
        if (filename.endsWith(".mp4") || filename.endsWith(".mov")
                || filename.endsWith(".avi") || filename.endsWith(".mkv"))
            return "video";
        if (filename.endsWith(".pdf") || filename.endsWith(".doc")
                || filename.endsWith(".docx") || filename.endsWith(".txt"))
            return "document";
        return null;
    }

    // -----------------------------------------------
    // HELPER
    // -----------------------------------------------
    private void ensureNotesDir() throws IOException {
        if (!Files.exists(NOTES_DIR)) Files.createDirectories(NOTES_DIR);
    }
}
