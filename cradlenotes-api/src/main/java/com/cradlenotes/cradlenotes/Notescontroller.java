package com.cradlenotes.cradlenotes;

import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * NotesController - REST API for CradleNotes
 *
 * Endpoints:
 *   GET    /api/notes           - list all notes
 *   GET    /api/notes/{file}    - get a specific note
 *   POST   /api/notes           - create a new note
 *   PUT    /api/notes/{file}    - update a note
 *   DELETE /api/notes/{file}    - delete a note
 */
@RestController
@RequestMapping("/api/notes")
@CrossOrigin(origins = "*")
public class NotesController {

    private static final Path NOTES_DIR =
            Path.of(System.getProperty("user.home"), ".notes", "notes");

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // -----------------------------------------------
    // GET /api/notes
    // Returns list of all notes with metadata
    // -----------------------------------------------
    @GetMapping
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
    // Returns full content of a specific note
    // -----------------------------------------------
    @GetMapping("/{filename}")
    public Map<String, String> getNote(@PathVariable String filename) throws IOException {
        Path filePath = NOTES_DIR.resolve(filename);

        if (!Files.exists(filePath)) {
            throw new RuntimeException("Note not found: " + filename);
        }

        List<String> lines = Files.readAllLines(filePath);
        Map<String, String> note = parseYamlHeader(filePath);
        note.put("filename", filename);

        // Find where content starts (after second ---)
        int contentStart = 0;
        int dashCount = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals("---")) {
                dashCount++;
                if (dashCount == 2) {
                    contentStart = i + 1;
                    break;
                }
            }
        }

        String content = String.join("\n",
                lines.subList(contentStart, lines.size())).trim();
        note.put("content", content);

        return note;
    }

    // -----------------------------------------------
    // POST /api/notes
    // Creates a new note
    // Body: { "title": "My Note", "content": "Hello!" }
    // -----------------------------------------------
    @PostMapping
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
    // Updates an existing note
    // Body: { "content": "Updated content" }
    // -----------------------------------------------
    @PutMapping("/{filename}")
    public Map<String, String> updateNote(
            @PathVariable String filename,
            @RequestBody Map<String, String> body) throws IOException {

        Path filePath = NOTES_DIR.resolve(filename);

        if (!Files.exists(filePath)) {
            throw new RuntimeException("Note not found: " + filename);
        }

        String newContent = body.getOrDefault("content", "");
        Map<String, String> meta = parseYamlHeader(filePath);
        String now = LocalDateTime.now().format(TIMESTAMP_FORMAT);

        String fileContent = String.format("""
                ---
                title: %s
                created: %s
                modified: %s
                tags: %s
                author: %s
                ---

                %s
                """,
                meta.getOrDefault("title",   ""),
                meta.getOrDefault("created", now),
                now,
                meta.getOrDefault("tags",    "[]"),
                meta.getOrDefault("author",  ""),
                newContent
        );

        Files.writeString(filePath, fileContent);

        Map<String, String> response = new HashMap<>();
        response.put("filename", filename);
        response.put("message", "Note updated successfully");
        return response;
    }

    // -----------------------------------------------
    // DELETE /api/notes/{filename}
    // Deletes a note
    // -----------------------------------------------
    @DeleteMapping("/{filename}")
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
    // YAML PARSER
    // -----------------------------------------------

    private Map<String, String> parseYamlHeader(Path filePath) {
        Map<String, String> metadata = new LinkedHashMap<>();
        try {
            List<String> lines = Files.readAllLines(filePath);

            if (lines.isEmpty() || !lines.get(0).trim().equals("---")) {
                metadata.put("title", filePath.getFileName().toString());
                return metadata;
            }

            int yamlEnd = -1;
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).trim().equals("---")) {
                    yamlEnd = i;
                    break;
                }
            }

            if (yamlEnd == -1) {
                metadata.put("title", filePath.getFileName().toString());
                return metadata;
            }

            for (int i = 1; i < yamlEnd; i++) {
                String line = lines.get(i).trim();
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    metadata.put(parts[0].trim(), parts[1].trim());
                }
            }

        } catch (IOException e) {
            metadata.put("error", e.getMessage());
        }
        return metadata;
    }

    // -----------------------------------------------
    // HELPER
    // -----------------------------------------------

    private void ensureNotesDir() throws IOException {
        if (!Files.exists(NOTES_DIR)) {
            Files.createDirectories(NOTES_DIR);
        }
    }
}
