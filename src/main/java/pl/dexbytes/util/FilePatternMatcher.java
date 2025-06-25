package pl.dexbytes.util;

import java.io.IOException;
import java.nio.file.*;

public class FilePatternMatcher implements PathMatcher{
    private final PathMatcher srcMatcher;
    private final PathMatcher gradleMatcher;
    private final PathMatcher dockerComposeMatcher;
    private final PathMatcher dockerfileMatcher;
    private final PathMatcher[] exclusionMatchers;

    public FilePatternMatcher() {
        FileSystem fileSystem = FileSystems.getDefault();
        this.srcMatcher = fileSystem.getPathMatcher("glob:src/**/*.*");
        this.gradleMatcher = fileSystem.getPathMatcher("glob:*.gradle");
        this.dockerComposeMatcher = fileSystem.getPathMatcher("glob:docker-compose*.{yml,yaml}");
        this.dockerfileMatcher = fileSystem.getPathMatcher("glob:Dockerfile*");

        // Common files to exclude
        this.exclusionMatchers = new PathMatcher[] {
                fileSystem.getPathMatcher("glob:**/.DS_Store"),
                fileSystem.getPathMatcher("glob:**/Thumbs.db"),
                fileSystem.getPathMatcher("glob:**/.gitignore"),
                fileSystem.getPathMatcher("glob:**/*.tessdata"),
                fileSystem.getPathMatcher("glob:**/*.traineddata"),
                fileSystem.getPathMatcher("glob:**/*.mp3"),
                fileSystem.getPathMatcher("glob:**/*.zip"),
        };
    }

    @Override
    public boolean matches(Path path) {
        // Check exclusions first
        for (PathMatcher exclusion : exclusionMatchers) {
            if (exclusion.matches(path)) {
                return false;
            }
        }

        // Check if file is binary by content (for files without clear extensions)
        if (Files.isRegularFile(path) && isBinaryFile(path)) {
            return false;
        }

        // Then check inclusions
        return srcMatcher.matches(path) ||
                gradleMatcher.matches(path) ||
                dockerComposeMatcher.matches(path) ||
                dockerfileMatcher.matches(path);
    }

    private boolean isBinaryFile(Path path) {
        try {
            // Read first 8KB of the file to determine if it's binary
            byte[] bytes = Files.readAllBytes(path);
            int bytesToCheck = Math.min(8192, bytes.length);

            // Check for null bytes and control characters (except tabs, newlines, carriage returns)
            for (int i = 0; i < bytesToCheck; i++) {
                byte b = bytes[i];
                if (b == 0) {
                    return true; // Null byte indicates binary file
                }
                // Allow tabs (9), newlines (10), carriage returns (13), and printable ASCII characters
                if (b < 9 || (b > 13 && b < 32)) {
                    return true; // Control character indicates binary file
                }
            }
            return false; // No binary content found
        } catch (IOException e) {
            // If we can't read the file, assume it's binary to be safe
            return true;
        }

    }

}
