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


    private File resolveProjectRoot() {
        File dir = new File(".").getAbsoluteFile();
        // Go up to 5 levels to search for project root
        for (int i = 0; i < 5; i++) {
            if (dir == null) break;
            
            File gatewayDir = new File(dir, "gateway-service");
            File logserviceDir = new File(dir, "logservice");
            if (gatewayDir.isDirectory() && logserviceDir.isDirectory()) {
                return dir;
            }
            dir = dir.getParentFile();
        }
        // Fallback to current working directory
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
        try (var stream = Files.walk(directory.toPath(), 10)) {
            Path foundPath = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(targetFileName))
                    .findFirst()
                    .orElse(null);
            return foundPath != null ? foundPath.toFile() : null;
        } catch (Exception e) {
            LOGGER.error("Error searching recursively for {}: {}", targetFileName, e.getMessage());
            return null;
        }
    }
}
