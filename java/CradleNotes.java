import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * CradleNotes - Your Personal Notes Manager
 *
 * Usage:
 *   java CradleNotes help
 *   java CradleNotes list
 *   java CradleNotes read <filename>
 *   java CradleNotes create "Title" "Content"
 *   java CradleNotes delete <filename>
 *   java CradleNotes edit <filename> "New content"
 *   java CradleNotes search <keyword>
 *   java CradleNotes attach <filename> <media-path>
 *   java CradleNotes link <filename> <url>
 */
public class CradleNotes {

    private static final Path NOTES_DIR =
            Path.of(System.getProperty("user.home"), ".notes");

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    // -----------------------------------------------
    // SETUP
    // -----------------------------------------------

    private static Path setup() {
        try {
            if (!Files.exists(NOTES_DIR)) {
                Files.createDirectories(NOTES_DIR);
                System.out.println("Created notes directory: " + NOTES_DIR);
            }
        } catch (IOException e) {
            System.err.println("Error creating notes directory: " + e.getMessage());
            System.exit(1);
        }
        return NOTES_DIR;
    }

    // -----------------------------------------------
    // HELP
    // -----------------------------------------------

    private static void showHelp() {
        String helpText = String.format("""
                CradleNotes - Your Personal Notes Manager v0.3

                Usage: java CradleNotes [command]

                Available commands:
                  help                          - Display this help information
                  list                          - List all notes
                  read <filename>               - Read a specific note
                  create "Title" "Content"      - Create a new note
                  delete <filename>             - Delete a note
                  edit <filename> "Content"     - Edit a note's content
                  search <keyword>              - Search notes by keyword
                  attach <filename> <filepath>  - Attach image, audio, video, or document
                  link <filename> <url>         - Add a web link to a note

                Supported media types:
                  Images:    jpg, jpeg, png, gif, webp
                  Audio:     mp3, wav, m4a, aac
                  Video:     mp4, mov, avi, mkv
                  Documents: pdf, doc, docx, txt

                Notes directory: %s
                Media directory: %s/media/
                """, NOTES_DIR, NOTES_DIR);
        System.out.println(helpText.trim());
    }

    // -----------------------------------------------
    // LIST
    // -----------------------------------------------

    private static boolean listNotes(Path notesDir) {
        if (!Files.exists(notesDir)) {
            System.err.println("Error: Notes directory does not exist: " + notesDir);
            return false;
        }

        Path notesSubdir = notesDir.resolve("notes");
        Path searchDir = Files.exists(notesSubdir) ? notesSubdir : notesDir;

        List<Path> noteFiles;
        try (Stream<Path> paths = Files.walk(searchDir, 1)) {
            noteFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".md")
                            || name.endsWith(".note")
                            || name.endsWith(".txt");
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            System.err.println("Error reading notes directory: " + e.getMessage());
            return false;
        }

        if (noteFiles.isEmpty()) {
            System.out.println("No notes found in " + searchDir);
            System.out.println("Create one: java CradleNotes create \"Title\" \"Content\"");
            return true;
        }

        System.out.println("\nNotes in " + searchDir + ":");
        System.out.println("=".repeat(60));

        for (Path noteFile : noteFiles) {
            Map<String, List<String>> meta = parseYamlHeaderMulti(noteFile);

            System.out.println("\n  File:    " + noteFile.getFileName());
            System.out.println("  Title:   " + getFirst(meta, "title", noteFile.getFileName().toString()));

            String created = getFirst(meta, "created", "");
            String tags    = getFirst(meta, "tags",    "");
            if (!created.isEmpty()) System.out.println("  Created: " + created);
            if (!tags.isEmpty())    System.out.println("  Tags:    " + tags);

            // Show all media attachments
            printAllValues(meta, "image",    "  Image:   ");
            printAllValues(meta, "audio",    "  Audio:   ");
            printAllValues(meta, "video",    "  Video:   ");
            printAllValues(meta, "document", "  Doc:     ");
            printAllValues(meta, "link",     "  Link:    ");
        }

        System.out.println("\n" + noteFiles.size() + " note(s) found.");
        return true;
    }

    // -----------------------------------------------
    // READ
    // -----------------------------------------------

    private static void readNote(String[] args) {
        if (args.length < 2) {
            System.err.println("Error: No filename provided.");
            System.err.println("Usage: java CradleNotes read <filename>");
            System.err.println("Tip:   run 'java CradleNotes list' to see filenames.");
            return;
        }

        String filename = args[1];
        Path filePath = resolveNotePath(filename);

        if (!Files.exists(filePath)) {
            System.err.println("Error: Note not found: " + filename);
            System.err.println("Tip:   run 'java CradleNotes list' to see available notes.");
            return;
        }

        try {
            List<String> lines = Files.readAllLines(filePath);
            Map<String, List<String>> meta = parseYamlHeaderMulti(filePath);

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

            System.out.println("\n" + "=".repeat(60));
            System.out.println(getFirst(meta, "title", filename));
            System.out.println("=".repeat(60));
            System.out.println("Created:  " + getFirst(meta, "created",  "unknown"));
            System.out.println("Modified: " + getFirst(meta, "modified", "unknown"));

            String tags   = getFirst(meta, "tags",   "");
            String author = getFirst(meta, "author", "");
            if (!tags.isEmpty())   System.out.println("Tags:     " + tags);
            if (!author.isEmpty()) System.out.println("Author:   " + author);

            // Show all media attachments
            printAllValues(meta, "image",    "Image:    ");
            printAllValues(meta, "audio",    "Audio:    ");
            printAllValues(meta, "video",    "Video:    ");
            printAllValues(meta, "document", "Document: ");
            printAllValues(meta, "link",     "Link:     ");

            System.out.println("-".repeat(60));
            for (int i = contentStart; i < lines.size(); i++) {
                System.out.println(lines.get(i));
            }
            System.out.println("-".repeat(60));

        } catch (IOException e) {
            System.err.println("Error reading note: " + e.getMessage());
        }
    }

    // -----------------------------------------------
    // CREATE
    // -----------------------------------------------

    private static void createNote(String[] args) {
        if (args.length < 3) {
            System.err.println("Error: please say something.");
            System.err.println("Usage: java CradleNotes create \"Title\" \"Content\"");
            return;
        }

        String title   = args[1];
        String content = args[2];

        if (title.isBlank()) {
            System.err.println("Error: Title cannot be blank.");
            return;
        }

        String safeTitle = title.toLowerCase()
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9\\-]", "")
                + ".note";

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

        try {
            Path notesSubdir = NOTES_DIR.resolve("notes");
            if (!Files.exists(notesSubdir)) {
                Files.createDirectories(notesSubdir);
            }
            Path filePath = notesSubdir.resolve(safeTitle);
            Files.writeString(filePath, fileContent);
            System.out.println("Note created: " + safeTitle);
        } catch (IOException e) {
            System.err.println("Error creating note: " + e.getMessage());
        }
    }

    // -----------------------------------------------
    // DELETE
    // -----------------------------------------------

    private static void deleteNote(String[] args) {
        if (args.length < 2) {
            System.err.println("Error: No filename provided.");
            System.err.println("Usage: java CradleNotes delete <filename>");
            return;
        }

        String filename = args[1];
        Path filePath = resolveNotePath(filename);

        if (!Files.exists(filePath)) {
            System.err.println("Error: Note not found: " + filename);
            return;
        }

        System.out.print("Are you sure you want to delete '" + filename + "'? (yes/no): ");
        Scanner scanner = new Scanner(System.in);
        String answer = scanner.nextLine().trim().toLowerCase();

        if (answer.equals("yes")) {
            try {
                Files.delete(filePath);
                System.out.println("Note deleted: " + filename);
            } catch (IOException e) {
                System.err.println("Error deleting note: " + e.getMessage());
            }
        } else {
            System.out.println("Deletion cancelled.");
        }
    }

    // -----------------------------------------------
    // EDIT
    // -----------------------------------------------

    private static void editNote(String[] args) {
        if (args.length < 3) {
            System.err.println("Error: please provide a filename and new content.");
            System.err.println("Usage: java CradleNotes edit <filename> \"New content\"");
            return;
        }

        String filename   = args[1];
        String newContent = args[2];
        Path filePath = resolveNotePath(filename);

        if (!Files.exists(filePath)) {
            System.err.println("Error: Note not found: " + filename);
            return;
        }

        try {
            // Rebuild YAML keeping ALL existing fields including media
            List<String> lines = Files.readAllLines(filePath);
            Map<String, List<String>> meta = parseYamlHeaderMulti(filePath);

            String now = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            StringBuilder yaml = new StringBuilder();
            yaml.append("---\n");
            yaml.append("title: ").append(getFirst(meta, "title", "")).append("\n");
            yaml.append("created: ").append(getFirst(meta, "created", now)).append("\n");
            yaml.append("modified: ").append(now).append("\n");
            yaml.append("tags: ").append(getFirst(meta, "tags", "[]")).append("\n");
            yaml.append("author: ").append(getFirst(meta, "author", "")).append("\n");

            // Keep all media attachments
            appendAllValues(yaml, meta, "image");
            appendAllValues(yaml, meta, "audio");
            appendAllValues(yaml, meta, "video");
            appendAllValues(yaml, meta, "document");
            appendAllValues(yaml, meta, "link");

            yaml.append("---\n\n");
            yaml.append(newContent).append("\n");

            Files.writeString(filePath, yaml.toString());
            System.out.println("Note updated: " + filename);

        } catch (IOException e) {
            System.err.println("Error updating note: " + e.getMessage());
        }
    }

    // -----------------------------------------------
    // SEARCH
    // -----------------------------------------------

    private static void searchNotes(String[] args) {
        if (args.length < 2) {
            System.err.println("Error: please provide a search term.");
            System.err.println("Usage: java CradleNotes search <keyword>");
            return;
        }

        String keyword = args[1].toLowerCase();
        Path notesSubdir = NOTES_DIR.resolve("notes");
        Path searchDir = Files.exists(notesSubdir) ? notesSubdir : NOTES_DIR;

        List<Path> noteFiles;
        try (Stream<Path> paths = Files.walk(searchDir, 1)) {
            noteFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".md")
                            || name.endsWith(".note")
                            || name.endsWith(".txt");
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            System.err.println("Error reading notes directory: " + e.getMessage());
            return;
        }

        if (noteFiles.isEmpty()) {
            System.out.println("No notes found.");
            return;
        }

        int found = 0;
        System.out.println("\nSearch results for: \"" + args[1] + "\"");
        System.out.println("=".repeat(60));

        for (Path noteFile : noteFiles) {
            try {
                String fileContent = Files.readString(noteFile).toLowerCase();
                if (fileContent.contains(keyword)) {
                    Map<String, List<String>> metadata = parseYamlHeaderMulti(noteFile);
                    System.out.println("\n  File:  " + noteFile.getFileName());
                    System.out.println("  Title: " + getFirst(metadata, "title", noteFile.getFileName().toString()));
                    found++;
                }
            } catch (IOException e) {
                System.err.println("Could not read: " + noteFile.getFileName());
            }
        }

        System.out.println();
        if (found == 0) {
            System.out.println("No notes found containing \"" + args[1] + "\"");
        } else {
            System.out.println(found + " note(s) found.");
        }
    }

    // -----------------------------------------------
    // ATTACH MEDIA
    // -----------------------------------------------

    private static void attachMedia(String[] args) {
        if (args.length < 3) {
            System.err.println("Error: Please provide a note filename and media path.");
            System.err.println("Usage: java CradleNotes attach <filename> <media-path>");
            return;
        }

        String filename  = args[1];
        String mediaPath = args[2];

        Path notePath = resolveNotePath(filename);
        if (!Files.exists(notePath)) {
            System.err.println("Error: Note not found: " + filename);
            return;
        }

        Path sourceFile = Path.of(mediaPath);
        if (!Files.exists(sourceFile)) {
            System.err.println("Error: File not found: " + mediaPath);
            return;
        }

        String originalName = sourceFile.getFileName().toString();
        String mediaType = detectMediaType(originalName.toLowerCase());

        if (mediaType == null) {
            System.err.println("Error: Unsupported file type.");
            System.err.println("Supported: jpg, png, gif, webp, mp3, wav, m4a, mp4, mov, pdf, doc, docx");
            return;
        }

        try {
            // Create media subfolder
            Path mediaDir = NOTES_DIR.resolve("media").resolve(mediaType);
            if (!Files.exists(mediaDir)) {
                Files.createDirectories(mediaDir);
            }

            // Add timestamp so files never overwrite each other
            String ext       = originalName.substring(originalName.lastIndexOf("."));
            String baseName  = originalName.substring(0, originalName.lastIndexOf("."));
            String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP);
            String newName   = baseName + "-" + timestamp + ext;

            Path destFile = mediaDir.resolve(newName);
            Files.copy(sourceFile, destFile, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("File saved: " + destFile);

            // Load existing note and ADD new media — keep everything else
            Map<String, List<String>> meta = parseYamlHeaderMulti(notePath);
            List<String> lines = Files.readAllLines(notePath);

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

            // Add new media to the existing list for that type
            meta.computeIfAbsent(mediaType, k -> new ArrayList<>())
                .add(destFile.toString());

            // Rebuild the file keeping ALL fields and ALL media
            String now = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            StringBuilder yaml = new StringBuilder();
            yaml.append("---\n");
            yaml.append("title: ").append(getFirst(meta, "title", "")).append("\n");
            yaml.append("created: ").append(getFirst(meta, "created", now)).append("\n");
            yaml.append("modified: ").append(now).append("\n");
            yaml.append("tags: ").append(getFirst(meta, "tags", "[]")).append("\n");
            yaml.append("author: ").append(getFirst(meta, "author", "")).append("\n");

            // Write ALL media entries
            appendAllValues(yaml, meta, "image");
            appendAllValues(yaml, meta, "audio");
            appendAllValues(yaml, meta, "video");
            appendAllValues(yaml, meta, "document");
            appendAllValues(yaml, meta, "link");

            yaml.append("---\n\n");
            yaml.append(content).append("\n");

            Files.writeString(notePath, yaml.toString());
            System.out.println("Note updated with " + mediaType + ": " + filename);

        } catch (IOException e) {
            System.err.println("Error attaching media: " + e.getMessage());
        }
    }

    // -----------------------------------------------
    // LINK
    // -----------------------------------------------

    private static void attachLink(String[] args) {
        if (args.length < 3) {
            System.err.println("Error: Please provide a note filename and URL.");
            System.err.println("Usage: java CradleNotes link <filename> <url>");
            return;
        }

        String filename = args[1];
        String url      = args[2];

        Path notePath = resolveNotePath(filename);
        if (!Files.exists(notePath)) {
            System.err.println("Error: Note not found: " + filename);
            return;
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            System.err.println("Error: URL must start with http:// or https://");
            return;
        }

        try {
            Map<String, List<String>> meta = parseYamlHeaderMulti(notePath);
            List<String> lines = Files.readAllLines(notePath);

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

            // Add new link to existing links
            meta.computeIfAbsent("link", k -> new ArrayList<>()).add(url);

            String now = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            StringBuilder yaml = new StringBuilder();
            yaml.append("---\n");
            yaml.append("title: ").append(getFirst(meta, "title", "")).append("\n");
            yaml.append("created: ").append(getFirst(meta, "created", now)).append("\n");
            yaml.append("modified: ").append(now).append("\n");
            yaml.append("tags: ").append(getFirst(meta, "tags", "[]")).append("\n");
            yaml.append("author: ").append(getFirst(meta, "author", "")).append("\n");

            appendAllValues(yaml, meta, "image");
            appendAllValues(yaml, meta, "audio");
            appendAllValues(yaml, meta, "video");
            appendAllValues(yaml, meta, "document");
            appendAllValues(yaml, meta, "link");

            yaml.append("---\n\n");
            yaml.append(content).append("\n");

            Files.writeString(notePath, yaml.toString());
            System.out.println("Link added to note: " + filename);
            System.out.println("URL: " + url);

        } catch (IOException e) {
            System.err.println("Error adding link: " + e.getMessage());
        }
    }

    // -----------------------------------------------
    // DETECT MEDIA TYPE
    // -----------------------------------------------

    private static String detectMediaType(String filename) {
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")
                || filename.endsWith(".png") || filename.endsWith(".gif")
                || filename.endsWith(".webp")) return "image";
        if (filename.endsWith(".mp3") || filename.endsWith(".wav")
                || filename.endsWith(".m4a") || filename.endsWith(".aac")) return "audio";
        if (filename.endsWith(".mp4") || filename.endsWith(".mov")
                || filename.endsWith(".avi") || filename.endsWith(".mkv")) return "video";
        if (filename.endsWith(".pdf") || filename.endsWith(".doc")
                || filename.endsWith(".docx") || filename.endsWith(".txt")) return "document";
        return null;
    }

    // -----------------------------------------------
    // YAML PARSER - supports multiple values per key
    // -----------------------------------------------

    /**
     * Parses YAML header into a map where each key can have multiple values.
     * This allows multiple image:, audio:, link: entries in one note.
     */
    private static Map<String, List<String>> parseYamlHeaderMulti(Path filePath) {
        Map<String, List<String>> metadata = new LinkedHashMap<>();
        try {
            List<String> lines = Files.readAllLines(filePath);

            if (lines.isEmpty() || !lines.get(0).trim().equals("---")) {
                metadata.computeIfAbsent("title", k -> new ArrayList<>())
                        .add(filePath.getFileName().toString());
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
                metadata.computeIfAbsent("title", k -> new ArrayList<>())
                        .add(filePath.getFileName().toString());
                return metadata;
            }

            for (int i = 1; i < yamlEnd; i++) {
                String line = lines.get(i).trim();
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    String key   = parts[0].trim();
                    String value = parts[1].trim();
                    // Each key can have multiple values (e.g. multiple images)
                    metadata.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
                }
            }

        } catch (IOException e) {
            System.err.println("Could not read file: " + filePath.getFileName());
        }
        return metadata;
    }

    // -----------------------------------------------
    // HELPERS
    // -----------------------------------------------

    private static String getFirst(Map<String, List<String>> meta, String key, String defaultVal) {
        List<String> values = meta.get(key);
        return (values != null && !values.isEmpty()) ? values.get(0) : defaultVal;
    }

    private static void printAllValues(Map<String, List<String>> meta, String key, String label) {
        List<String> values = meta.get(key);
        if (values != null) {
            for (String v : values) {
                if (!v.isEmpty()) System.out.println(label + v);
            }
        }
    }

    private static void appendAllValues(StringBuilder sb,
            Map<String, List<String>> meta, String key) {
        List<String> values = meta.get(key);
        if (values != null) {
            for (String v : values) {
                if (!v.isEmpty()) sb.append(key).append(": ").append(v).append("\n");
            }
        }
    }

    private static Path resolveNotePath(String filename) {
        Path notesSubdir = NOTES_DIR.resolve("notes");
        Path searchDir = Files.exists(notesSubdir) ? notesSubdir : NOTES_DIR;
        return searchDir.resolve(filename);
    }

    // -----------------------------------------------
    // FINISH
    // -----------------------------------------------

    private static void finish(int exitCode) {
        System.exit(exitCode);
    }

    // -----------------------------------------------
    // MAIN
    // -----------------------------------------------

    public static void main(String[] args) {
        Path notesDir = setup();

        if (args.length < 1) {
            System.err.println("Error: No command provided.");
            System.err.println("Usage: java CradleNotes [command]");
            System.err.println("Try 'java CradleNotes help' for more information.");
            finish(1);
            return;
        }

        String command = args[0].toLowerCase();

        switch (command) {
            case "help"   -> { showHelp();           finish(0); }
            case "list"   -> { listNotes(notesDir);  finish(0); }
            case "read"   -> { readNote(args);        finish(0); }
            case "create" -> { createNote(args);      finish(0); }
            case "delete" -> { deleteNote(args);      finish(0); }
            case "edit"   -> { editNote(args);        finish(0); }
            case "search" -> { searchNotes(args);     finish(0); }
            case "attach" -> { attachMedia(args);     finish(0); }
            case "link"   -> { attachLink(args);      finish(0); }
            default -> {
                System.err.println("Error: Unknown command '" + command + "'");
                System.err.println("Try 'java CradleNotes help' for more information.");
                finish(1);
            }
        }
    }
}
