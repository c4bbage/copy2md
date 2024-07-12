package bf.com.copy2md;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static bf.com.copy2md.CopyCodeAsMarkdownAction.getRelativePath;
import static bf.com.copy2md.CopyFileAsMarkdownAction.escapeMarkdown;
import static bf.com.copy2md.CopyFileAsMarkdownAction.isImageFile;

class CopyAllOpenedTabsAsMarkdownAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        // 获取所有打开的文件
        VirtualFile[] allOpenFiles = FileEditorManager.getInstance(project).getOpenFiles();

        if (allOpenFiles.length == 0) {
            return; // 没有打开的文件
        }

        // 创建一个StringBuilder来存储所有文件的Markdown内容
        StringBuilder allFilesMarkdown = new StringBuilder();

        // 遍历所有打开的文件
        for (VirtualFile file : allOpenFiles) {
            // 使用CopyFileAsMarkdownAction来获取每个文件的Markdown内容
            try {
                if (isImageFile(file)) {
                    String safeFileName = escapeMarkdown(file.getName());
                    allFilesMarkdown.append("![Image: ").append(safeFileName).append("](")
                            .append(getRelativePath(project,file)).append(")\n\n");
                    continue;
                }
                String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
                allFilesMarkdown.append("## File: ").append(getRelativePath(project,file)).append("\n\n");
                allFilesMarkdown.append("```").append(file.getExtension()).append("\n");
                allFilesMarkdown.append(content).append("\n");
                allFilesMarkdown.append("```\n\n");
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }

        }

        // 将所有文件的Markdown内容复制到剪贴板
        CopyPasteManager.getInstance().setContents(new StringSelection(allFilesMarkdown.toString()));
    }
}