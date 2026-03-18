import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.nio.file.attribute.BasicFileAttributes;
/**
 * Future Proof Notes Manager - Version Zero (CLI)
 * A personal notes manager using text files with YAML headers.
 * Command-line interface version.
 */
public class Notes0 {

    private static final Path NOTES_DIR = Path.of(System.getProperty("user.home"), ".notes");

    /**
     * Initialize the notes application.
     */
    private static Path setup() {
        // Define the notes directory in HOME
        // Check if notes directory exists (silent check for CLI version)
        // It will be shown if needed by specific commands
        return NOTES_DIR;
    }

    /**
     * Display help information.
     */
    private static void showHelp() {
        String helpText = String.format("""
                Future Proof Notes Manager v0.0

                Usage: java Notes0 [command]

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

        private static boolean listNotes(Path notesDir) {
        // Check if notes directory exists
        if (!Files.exists(notesDir)) {
            System.err.println("Error: Notes directory does not exist: " + notesDir);
            System.err.println("Create it with: mkdir -p ~/.notes/notes");
            System.err.println("Then copy test notes: cp test-notes/*.md ~/.notes/notes/");
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
            System.out.println("No notes found in " + notesDir);
            System.err.println("Copy test notes with: cp test-notes/*.md ~/.notes/");
            return true;
        }

        // Parse and display notes
        System.out.println("Notes in " + notesDir + ":");
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


private static void finish(int exitCode) {
        System.exit(exitCode);
    }


    public static void main(String[] args) {
        // Setup
        Path notesDir = setup();

        // Parse command-line arguments
        if (args.length < 1) {
            // No command provided
            System.err.println("Error: No command provided.");
            System.err.println("Usage: java Notes0 [command]");
            System.err.println("Try 'java Notes0 help' for more information.");
            finish(1);
        }

        String command = args[0].toLowerCase();

        // Process command
        switch (command) {
            case "help":
                showHelp();
                finish(0);
                break;
            default:
                System.err.println("Error: Unknown command '" + command + "'");
                System.err.println("Try 'java Notes0 help' for more information.");
                finish(1);
        }
    }
}
