package com.bf.copy2md.formatter;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarkdownFormatter {
    public String formatFileContent(Project project, VirtualFile file, String content) {
        StringBuilder markdown = new StringBuilder();
        
        // Add file path as header
        Path projectPath = Paths.get(project.getBasePath());
        Path filePath = Paths.get(file.getPath());
        Path relativePath = projectPath.relativize(filePath);
        
        markdown.append("## File: ").append(relativePath).append("\n\n");
        
        // Add code block with language
        String fileExtension = file.getExtension();
        markdown.append("```").append(fileExtension != null ? fileExtension : "").append("\n");
        markdown.append(content).append("\n");
        markdown.append("```\n\n");
        
        return markdown.toString();
    }
}
