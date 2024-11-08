package bf.com.copy2md.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
public class CopyFileAsMarkdownAction extends AnAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        @NotNull Project project = e.getRequiredData(CommonDataKeys.PROJECT);

        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (files == null || files.length == 0) return;

        StringBuilder markdown = new StringBuilder();
        markdown.append("Project Name: ").append(project.getName()).append("\n\n");
        for (VirtualFile file : files) {
            try {
                if (isImageFile(file)) {
                    String safeFileName = escapeMarkdown(file.getName());
                    markdown.append("![Image: ").append(safeFileName).append("](")
                            .append(getRelativePath(project,file)).append(")\n\n");
                    continue;
                }
                String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
                markdown.append("## File: ").append(getRelativePath(project,file)).append("\n\n");
                markdown.append("```").append(file.getExtension()).append("\n");
                markdown.append(content).append("\n");
                markdown.append("```\n\n");
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }

        CopyPasteManager.getInstance().setContents(new StringSelection(markdown.toString()));
    }
    // 辅助方法
    public static  boolean isImageFile(VirtualFile file) {
        String extension = file.getExtension();
        if (extension == null) return false;
        return Arrays.asList("png", "jpg", "jpeg", "gif", "bmp")
                .contains(extension.toLowerCase());
    }

    public static String escapeMarkdown(String text) {
        return text.replaceAll("([\\[\\]()\\\\])", "\\\\$1");
    }
    public static String getRelativePath(Project project, VirtualFile file) {
        VirtualFile projectDir = project.getBaseDir();
        if (projectDir == null) {
            return file.getPath();
        }

        Path projectPath = Paths.get(projectDir.getPath());
        Path filePath = Paths.get(file.getPath());

        Path relativePath = projectPath.relativize(filePath);
        return relativePath.toString().replace('\\', '/');
    }
}