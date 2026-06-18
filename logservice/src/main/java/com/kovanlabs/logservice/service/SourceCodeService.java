package com.kovanlabs.logservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Service
public class SourceCodeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SourceCodeService.class);
    private File projectRoot;

    public SourceCodeService() {
        this.projectRoot = resolveProjectRoot();
        LOGGER.info("Dynamic Project Root resolved to: {}", projectRoot.getAbsolutePath());
    }

    private boolean isValidProjectRoot(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return false;
        }
        
        String path = dir.getAbsolutePath();
        String userHome = System.getProperty("user.home");
        if (path.equals(userHome) || path.equals("/") || path.endsWith(":\\") || path.endsWith(":\\..")) {
            return false; // Skip system roots and user home directory to prevent incorrect mapping and home directory walks
        }
        
        // Check for presence of project folders
        File gitDir = new File(dir, ".git");
        File gatewayDir = new File(dir, "gateway-service");
        File logserviceDir = new File(dir, "logservice");
        File notificationDir = new File(dir, "notificationservice");
        File managementDir = new File(dir, "servicemanagementservice");
        
        return (gitDir.isDirectory() && gatewayDir.isDirectory() && logserviceDir.isDirectory() &&
                notificationDir.isDirectory() && managementDir.isDirectory());
    }

    private File resolveProjectRoot() {
        // 1. Try to traverse up from current working directory
        File dir = new File(".").getAbsoluteFile();
        for (int i = 0; i < 8; i++) {
            if (dir == null) break;
            if (isValidProjectRoot(dir)) {
                return dir;
            }
            // Check nested folder too (in case cwd is outside the repo directory but parent directory contains it)
            File nestedRoot = new File(dir, "log-aggregation-microservices");
            if (isValidProjectRoot(nestedRoot)) {
                return nestedRoot;
            }
            dir = dir.getParentFile();
        }

        // 2. Try to traverse up from System property user.dir
        String userDirProp = System.getProperty("user.dir");
        if (userDirProp != null) {
            dir = new File(userDirProp).getAbsoluteFile();
            for (int i = 0; i < 8; i++) {
                if (dir == null) break;
                if (isValidProjectRoot(dir)) {
                    return dir;
                }
                File nestedRoot = new File(dir, "log-aggregation-microservices");
                if (isValidProjectRoot(nestedRoot)) {
                    return nestedRoot;
                }
                dir = dir.getParentFile();
            }
        }

        // 3. Fallback to classpath entries (scanning parent directories but respecting the home-directory guard)
        String classPath = System.getProperty("java.class.path");
        if (classPath != null) {
            String separator = System.getProperty("path.separator", File.pathSeparator);
            String[] entries = classPath.split(separator);
            for (String entry : entries) {
                if (entry.contains("log-aggregation-microservices") || 
                    entry.contains("gateway-service") || 
                    entry.contains("logservice") || 
                    entry.contains("notificationservice") || 
                    entry.contains("servicemanagementservice")) {
                    
                    File entryFile = new File(entry).getAbsoluteFile();
                    dir = entryFile;
                    for (int i = 0; i < 8; i++) {
                        if (dir == null) break;
                        if (isValidProjectRoot(dir)) {
                            return dir;
                        }
                        File nestedRoot = new File(dir, "log-aggregation-microservices");
                        if (isValidProjectRoot(nestedRoot)) {
                            return nestedRoot;
                        }
                        dir = dir.getParentFile();
                    }
                }
            }
        }

        // 4. Ultimate fallback to check for any direct folders containing microservices in parent hierarchy
        // even if .git is missing (e.g. running in Docker container without .git)
        dir = new File(".").getAbsoluteFile();
        for (int i = 0; i < 8; i++) {
            if (dir == null) break;
            File gatewayDir = new File(dir, "gateway-service");
            File logserviceDir = new File(dir, "logservice");
            if (gatewayDir.isDirectory() && logserviceDir.isDirectory()) {
                return dir;
            }
            File nestedRoot = new File(dir, "log-aggregation-microservices");
            if (nestedRoot.isDirectory()) {
                File nestedGateway = new File(nestedRoot, "gateway-service");
                File nestedLogservice = new File(nestedRoot, "logservice");
                if (nestedGateway.isDirectory() && nestedLogservice.isDirectory()) {
                    return nestedRoot;
                }
            }
            dir = dir.getParentFile();
        }

        LOGGER.warn("Could not dynamically resolve a valid project root using strict validation. Falling back to current directory.");
        return new File(".").getAbsoluteFile();
    }


    public Map<String, Object> getSourceCode(String serviceName, String className, String fileName, Integer lineNumber) throws IOException {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("File name is required");
        }

        File targetFile = locateFile(serviceName, className, fileName);
        if (targetFile == null || !targetFile.exists() || !targetFile.isFile()) {
            LOGGER.warn("Source file not found: service={}, class={}, file={}", serviceName, className, fileName);
            return null;
        }


        String canonicalPath = targetFile.getCanonicalPath();
        String rootCanonicalPath = projectRoot.getCanonicalPath();
        if (!canonicalPath.startsWith(rootCanonicalPath)) {
            LOGGER.warn("Access denied (Path Traversal attempted): {} is not under {}", canonicalPath, rootCanonicalPath);
            throw new SecurityException("Unauthorized file path access");
        }

        String content = Files.readString(targetFile.toPath(), StandardCharsets.UTF_8);
        String relativePath = canonicalPath.substring(rootCanonicalPath.length())
                .replace('\\', '/');
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("service", serviceName);
        response.put("fileName", targetFile.getName());
        response.put("filePath", relativePath);
        response.put("lineNumber", lineNumber != null ? lineNumber : 1);
        response.put("fileContent", content);
        response.put("targetLine", lineNumber != null ? lineNumber : 1);

        return response;
    }


    private File locateFile(String serviceName, String className, String fileName) {
        if (serviceName == null || serviceName.isBlank()) {
            // General project wide fallback
            return findFileRecursively(projectRoot, fileName);
        }

        // Map service name to directory name
        String mappedFolder = serviceName.trim();
        if ("service-management-service".equalsIgnoreCase(mappedFolder)) {
            mappedFolder = "servicemanagementservice";
        } else if ("notification-service".equalsIgnoreCase(mappedFolder)) {
            mappedFolder = "notificationservice";
        } else if ("log-service".equalsIgnoreCase(mappedFolder)) {
            mappedFolder = "logservice";
        }

        File serviceFolder = new File(projectRoot, mappedFolder);
        if (!serviceFolder.isDirectory()) {
            // If the specific service directory doesn't exist, search the whole project
            return findFileRecursively(projectRoot, fileName);
        }

        // Standard Java layout mapping
        if (fileName.toLowerCase().endsWith(".java") || (className != null && className.contains("."))) {
            if (className != null && !className.isBlank()) {
                String classPath = className.replace('.', '/') + ".java";
                File standardJavaFile = new File(serviceFolder, "src/main/java/" + classPath);
                if (standardJavaFile.isFile()) {
                    return standardJavaFile;
                }
            }
            // Standard Java folder recursive search fallback
            File srcFolder = new File(serviceFolder, "src");
            if (srcFolder.isDirectory()) {
                File found = findFileRecursively(srcFolder, fileName);
                if (found != null) return found;
            }
        }

        // Python or direct file search within the service folder
        File found = findFileRecursively(serviceFolder, fileName);
        if (found != null) {
            return found;
        }

        // General fallback to project-wide search
        return findFileRecursively(projectRoot, fileName);
    }


    private File findFileRecursively(File directory, String targetFileName) {
        final File[] foundFile = new File[1];
        try {
            java.nio.file.Files.walkFileTree(directory.toPath(), new java.nio.file.SimpleFileVisitor<Path>() {
                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
                    String name = dir.getFileName().toString();
                    if (name.equals(".git") || name.equals("node_modules") || name.equals("target") || 
                        name.equals("dist") || name.equals(".idea") || name.equals("logs") || 
                        name.equals("out") || name.equals("build") || name.equals(".gradle") || 
                        name.equals("bin")) {
                        return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                    if (file.getFileName().toString().equalsIgnoreCase(targetFileName)) {
                        foundFile[0] = file.toFile();
                        return java.nio.file.FileVisitResult.TERMINATE;
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFileFailed(Path file, java.io.IOException exc) {
                    // Ignore access denied or other errors, continue searching
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            LOGGER.error("Error searching recursively for {}: {}", targetFileName, e.getMessage());
        }
        return foundFile[0];
    }

    /**
     * Safely applies a code fix to a relative file path under the project root.
     * Includes path traversal security validation, line-ending normalization, backup, and automatic rollback on error.
     *
     * @param relativeFilePath the relative path of the file to modify
     * @param originalCode     the exact block of code to search for
     * @param fixedCode        the new block of code to replace it with
     * @throws IOException       if filesystem operations fail
     * @throws SecurityException if path traversal is attempted
     */
    public void applyCodeFix(String relativeFilePath, String originalCode, String fixedCode) throws IOException {
        if (relativeFilePath == null || relativeFilePath.isBlank()) {
            throw new IllegalArgumentException("File path is required");
        }
        if (originalCode == null || originalCode.isEmpty()) {
            throw new IllegalArgumentException("Original code block cannot be empty");
        }

        // 1. Filesystem safety and path traversal protection
        Path rootPath = projectRoot.toPath().toAbsolutePath().normalize();
        Path targetPath = rootPath.resolve(relativeFilePath).normalize().toAbsolutePath();

        if (!targetPath.startsWith(rootPath)) {
            LOGGER.error("Path traversal attempt blocked: {} is not under project root {}", targetPath, rootPath);
            throw new SecurityException("Unauthorized file path access (Path Traversal attempted)");
        }

        File targetFile = targetPath.toFile();
        if (!targetFile.exists() || !targetFile.isFile()) {
            throw new java.io.FileNotFoundException("Target file not found: " + relativeFilePath);
        }

        // Read original file content
        String fileContent = Files.readString(targetPath, StandardCharsets.UTF_8);

        // 2. Line ending normalization
        String lineEnding = fileContent.contains("\r\n") ? "\r\n" : "\n";
        String normalizedOriginal = originalCode.replace("\r\n", "\n").replace("\n", lineEnding);
        String normalizedFixed = fixedCode.replace("\r\n", "\n").replace("\n", lineEnding);

        // 3. Verify original code block exists exactly
        if (!fileContent.contains(normalizedOriginal)) {
            // Check raw version in case normalization causes differences
            if (fileContent.contains(originalCode)) {
                normalizedOriginal = originalCode;
                normalizedFixed = fixedCode;
            } else {
                throw new IllegalArgumentException("The original code block was not found in the target file. The code might have changed.");
            }
        }

        // Ensure only one exact match or replace the first matching occurrence
        String updatedContent = fileContent.replace(normalizedOriginal, normalizedFixed);

        // 4. Create backup copy
        Path backupPath = targetPath.getParent().resolve(targetFile.getName() + ".bak");
        Files.writeString(backupPath, fileContent, StandardCharsets.UTF_8);
        LOGGER.debug("Created temporary backup file at {}", backupPath);

        try {
            // 5. Write modified content
            Files.writeString(targetPath, updatedContent, StandardCharsets.UTF_8);
            LOGGER.info("Applied fix successfully to {}", relativeFilePath);
        } catch (Exception e) {
            // 6. Rollback on failure
            LOGGER.error("Failed writing code fix to {}, rolling back from backup...", relativeFilePath, e);
            try {
                Files.writeString(targetPath, fileContent, StandardCharsets.UTF_8);
            } catch (Exception rollbackEx) {
                LOGGER.error("CRITICAL: Rollback failed for {}!", relativeFilePath, rollbackEx);
            }
            throw new IOException("Failed to write updated source code. Rollback initiated. Error: " + e.getMessage(), e);
        } finally {
            // Clean up backup file
            try {
                Files.deleteIfExists(backupPath);
            } catch (Exception cleanupEx) {
                LOGGER.warn("Failed to delete backup file {}: {}", backupPath, cleanupEx.getMessage());
            }
        }
    }
}
