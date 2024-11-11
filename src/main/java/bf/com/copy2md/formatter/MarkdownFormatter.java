package bf.com.copy2md.formatter;

import bf.com.copy2md.model.FunctionContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;

public class MarkdownFormatter {
    public String format(Set<FunctionContext> contexts) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Function Call Context\n\n");

        for (FunctionContext context : contexts) {
            markdown.append("## ").append(context.getName()).append("\n\n");
            markdown.append("File: ").append(context.getFileName()).append("\n");
            markdown.append("Package: ").append(context.getPackageName()).append("\n\n");
            markdown.append("```java\n");
            markdown.append(context.getSourceText()).append("\n");
            markdown.append("```\n\n");
        }

        return markdown.toString();
    }
    // 新增通用格式化方法
    public String formatFileContent(Project project, VirtualFile file, String content) {
        StringBuilder markdown = new StringBuilder();

        if (isImageFile(file)) {
            String safeFileName = escapeMarkdown(file.getName());
            markdown.append("![Image: ").append(safeFileName).append("](")
                    .append(getRelativePath(project, file)).append(")\n\n");
        } else {
            markdown.append("## File: ").append(getRelativePath(project, file)).append("\n\n");
            markdown.append("```").append(file.getExtension()).append("\n");
            markdown.append(content).append("\n");
            markdown.append("```\n\n");
        }

        return markdown.toString();
    }

    // 工具方法移到这里
    private boolean isImageFile(VirtualFile file) {
        String extension = file.getExtension();
        if (extension == null) return false;
        return Arrays.asList("png", "jpg", "jpeg", "gif", "bmp")
                .contains(extension.toLowerCase());
    }

    private String escapeMarkdown(String text) {
        return text.replaceAll("([\\[\\]()\\\\])", "\\\\$1");
    }

    private String getRelativePath(Project project, VirtualFile file) {
        VirtualFile projectDir = project.getBaseDir();
        if (projectDir == null) {
            return file.getPath();
        }

        Path projectPath = Paths.get(projectDir.getPath());
        Path filePath = Paths.get(file.getPath());

        Path relativePath = projectPath.relativize(filePath);
        return relativePath.toString().replace('\\', '/');    }
}