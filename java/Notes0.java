import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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

                Notes directory: %s
                """, NOTES_DIR);
        System.out.println(helpText.trim());
    }
public static void showList(List<Path> noteFiles, String tagFilter) {
    if (noteFiles.isEmpty()) {
        System.out.println("No notes found.");
        return;
    }
    int count = 0;
    System.out.println("\\nYour Notes");
    System.out.println("=".repeat(50));

    for (Path file : noteFiles) {
        try {
            Map<String, String> meta = loadMetadata(file);
            String title = meta.getOrDefault("title", "Untitled");
            String modified = meta.getOrDefault("modified", "Unknown");
            String tags = meta.getOrDefault("tags", "");
            String author = meta.getOrDefault("author", "Unknown");
            String status = meta.getOrDefault("status", "Unknown");

if (tagFilter != null && !tags.toLowerCase().contains(tagFilter.toLowerCase())) {
                continue;
            }
System.out.println(" File  : " + file.getFileName());
            System.out.println(" Title : " + title);
            System.out.println(" Modified: " + modified);
            if (!tags.isEmpty()) {
                System.out.println(" Tags  : " + tags);
            }
            if (!author.isEmpty()) {
                System.out.println(" Author: " + author);
            }
            if (!status.isEmpty()) {
                System.out.println(" Status: " + status);
            }
            System.out.println();
            count++;
        } catch (Exception e) {
            System.out.println("  (could not read: " + file.getFileName() + ")");
        }

if (count ==0 && tagFilter != null) {
    System.out.println("No notes found with tag: " + tagFilter);
} else {
    System.out.println(count + " note(s) found.");
  }
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
}