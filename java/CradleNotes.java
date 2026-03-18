import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Future Proof Notes Manager - Version One (CLI)
 * A personal notes manager using text files with YAML headers.
 * Command-line interface version with 'list' command.
 *
 * SETUP REMINDER:
 * Before running the 'list' command, copy the test notes to your notes directory:
 *     cp -r test-notes/* ~/.notes/
 * or create the directory structure:
 *     mkdir -p ~/.notes/notes
 *     cp test-notes/*.md ~/.notes/notes/
 */
public class CradleNotes {

    private static final Path NOTES_DIR = Path.of(System.getProperty("user.home"), ".notes");

    /**
     * Initialize the notes application.
     */
    private static Path setup() {
        // Define the notes directory in HOME
        // Check if notes directory exists
        // For CLI version, we don't automatically create it
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
// to go with Kris's list i copied this.
    private static void showHelp() {
        String helpText = String.format("""
                CradleNotes - Your Personal Notes Manager v0.1

                Usage: java CradleNotes [command]

                Available commands:
                help    - Display this help information
                list    - List all notes in the notes
                read <filename> - Read a specific note

                Notes directory: %s
                Setup : 
                mkdir -p ~/.notes/notes
                cp test-notes/*.md ~/.notes/notes/
                """, NOTES_DIR);
        System.out.println(helpText.trim());
    }


        /**
     * List all notes in the notes directory.
     */
    private static boolean listNotes(Path notesDir) {
        // Check if notes directory exists
        if (!Files.exists(notesDir)) {
            System.err.println("Error: Notes directory does not exist: " + notesDir);
            System.err.println("Create it with: mkdir -p ~/.notes/notes");
            
            return false;
        }

        // Look for notes in the notes directory (or directly in .notes)
        Path notesSubdir = notesDir.resolve("notes");
        Path searchDir = Files.exists(notesSubdir) ? notesSubdir : notesDir;

        // Find all note files (*.md, *.note, *.txt)
        List<Path> noteFiles;
        try (Stream<Path> paths = Files.walk(searchDir, 1)) {
            noteFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".md") || name.endsWith(".note") || name.endsWith(".txt");
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            System.err.println("Error reading notes directory: " + e.getMessage());
            return false;
        }

        if (noteFiles.isEmpty()) {
            System.out.println("No notes found in " + searchDir);
            System.err.println("Copy test notes with: cp test-notes/*.md ~/.notes/");
            return true;
        }

        // Parse and display notes
        System.out.println("\nNotes in " + searchDir + ":");
        System.out.println("=".repeat(60));

        for (Path noteFile : noteFiles) {
            // this should probably be a private method to be re-used
            Map<String, String> metadata = parseYamlHeader(noteFile);
            String title = metadata.getOrDefault("title", noteFile.getFileName().toString());
            String created = metadata.getOrDefault("created", "N/A");
            String tags = metadata.getOrDefault("tags", "");

            System.out.println("\n" + noteFile.getFileName());
            System.out.println("  Title: " + title);
            if (!created.equals("N/A")) {
                System.out.println("  Created: " + created);
            }
            if (!tags.isEmpty()) {
                System.out.println("  Tags: " + tags);
            }
        }

        System.out.println("\n" + noteFiles.size() + " note(s) found.");
        return true;
    }

    private static void readNote(String[] args) {
    if (args.length < 2) { //everything added after java cradlenotes , its 2 so its has to be more than 2
        System.err.println("Error: No filename provided.");
        System.err.println("Usage: java Notes1 read <filename>");
        System.err.println("Tip:   run 'java Notes1 list' to see filenames.");
        return;
    }

    String filename = args[1];

    Path notesSubdir = NOTES_DIR.resolve("notes");
    Path searchDir = Files.exists(notesSubdir) ? notesSubdir : NOTES_DIR;
    Path filePath = searchDir.resolve(filename);

    if (!Files.exists(filePath)) {
        System.err.println("Error: Note not found: " + filename);
        System.err.println("Tip:   run 'java Notes1 list' to see available notes.");
        return;
    }

    try {
        List<String> lines = Files.readAllLines(filePath);
        Map<String, String> meta = parseYamlHeader(filePath);

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

        // Print metadata
        System.out.println("\n" + "=".repeat(60));
        System.out.println(meta.getOrDefault("title", filename));
        System.out.println("=".repeat(60));
        System.out.println("Created:  " + meta.getOrDefault("created",  "unknown"));
        System.out.println("Modified: " + meta.getOrDefault("modified", "unknown"));

        String tags   = meta.getOrDefault("tags",   "");
        String author = meta.getOrDefault("author", "");
        if (!tags.isEmpty())   System.out.println("Tags:     " + tags);
        if (!author.isEmpty()) System.out.println("Author:   " + author);

        // Print content
        System.out.println("-".repeat(60));
        for (int i = contentStart; i < lines.size(); i++) {
            System.out.println(lines.get(i));
        }
        System.out.println("-".repeat(60));

    } catch (IOException e) {
        System.err.println("Error reading note: " + e.getMessage());
    }
}

   private static void String createNote(String[] args) {
    if (args.length < 3) {
        System.err.println("Error: please say something.");// this is what my notes will say to you if you fail to give enough information to create a simple note!!
        System.err.println("Usage: java CradleNotes create \"Title\" \"Content\"");
        return;
    }
    String title = args[1];
    String content = args[2];

if (title.isBlank()) {
    System.err.println("Error: Title cannot be blank.");
    return;
}
String safeTitle = title.toLowerCase()
.replaceAll("\\s+", "-")
.replaceAll("[^a-z0-9\\-]", "") + ".md";

String now = LocalDateTime.now().format(TIMESTAMP_FORMAT);
String fileContent = String.format("""
---
title: %s
created: %s
---

%s

        """, title, now, content);
try {
    Path notesSubdir =NOTES_DIR.resolve("notes");
    if (!Files.exists(notesSubdir)) {
        Files.createDirectories(notesSubdir);
    }
    Path filePath = notesSubdir.resolve(safeTitle);
    Files.writeString(filePath, fileContent);
    System.out.println("Note created: " + safeTitle);
} catch (Exception e) {
    System.out.println("Error creating note: " + e.getMessage());
    }
}

//delete


    /**
     * Parse YAML front matter from a note file.
     * Returns a map with metadata.
     */

    
    private static Map<String, String> parseYamlHeader(Path filePath) {
        Map<String, String> metadata = new LinkedHashMap<>();

        try {
            List<String> lines = Files.readAllLines(filePath);

            // Check if file starts with YAML front matter
            if (lines.isEmpty() || !lines.get(0).trim().equals("---")) {
                metadata.put("title", filePath.getFileName().toString());
                return metadata;
            }

            // Find the closing ---
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

            // Parse YAML lines (simple parsing for basic key: value pairs)
            for (int i = 1; i < yamlEnd; i++) {
                String line = lines.get(i).trim();
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    metadata.put(key, value);
                }
            }

        } catch (IOException e) {
            System.err.println("Could not read file: " + filePath.getFileName());
        }

        return metadata;
    }





    /**
     * Clean up and exit the application.
     */
    private static void finish(int exitCode) {
        System.exit(exitCode);
    }

    /**
     * Main entry point for the notes CLI application.
     */
    public static void main(String[] args) {
        // Setup
        Path notesDir = setup();

        // Parse command-line arguments
        if (args.length < 1) {
            // No command provided
            System.err.println("Error: No command provided.");
            System.err.println("Usage: java Notes1 [command]");
            System.err.println("Try 'java Notes1 help' for more information.");
            finish(1);
            return; // added return to avoid unreachable code warning
        }

        String command = args[0].toLowerCase();

        // Process command
        switch (command) {
            case "help":
                {showHelp();
                finish(0);}
               
            case "list":{
               listNotes(notesDir);
                finish(0);}

            case "read" : {readNote(args);
                finish(0);}
            default:
                System.err.println("Error: Unknown command '" + command + "'");
                System.err.println("Try 'java Notes1 help' for more information.");
                finish(1);
        }
    }
}
}