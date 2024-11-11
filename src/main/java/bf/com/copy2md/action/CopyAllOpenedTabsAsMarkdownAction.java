package bf.com.copy2md.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
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
import bf.com.copy2md.formatter.MarkdownFormatter;
import bf.com.copy2md.util.CopyUtil;

class CopyAllOpenedTabsAsMarkdownAction extends AnAction {
    private final MarkdownFormatter formatter = new MarkdownFormatter();

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
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
        allFilesMarkdown.append("Project Name: ").append(project.getName()).append("\n\n");
        // 遍历所有打开的文件
        for (VirtualFile file : allOpenFiles) {
            try {
                    String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
                    allFilesMarkdown.append(formatter.formatFileContent(project, file, content));

            } catch (IOException ioException) {
                ioException.printStackTrace();
            }

        }

        // 将所有文件的Markdown内容复制到剪贴板
        CopyUtil.copyToClipboardWithNotification(allFilesMarkdown.toString(), project);
    }
}