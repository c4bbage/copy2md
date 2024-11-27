package com.bf.copy2md.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import java.nio.charset.StandardCharsets;
import com.bf.copy2md.formatter.MarkdownFormatter;
import com.bf.copy2md.util.CopyUtil;

public class CopyAllOpenedTabsAsMarkdownAction extends AnAction {
    private final MarkdownFormatter formatter = new MarkdownFormatter();

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        VirtualFile[] allOpenFiles = FileEditorManager.getInstance(project).getOpenFiles();
        if (allOpenFiles.length == 0) return;

        StringBuilder allFilesMarkdown = new StringBuilder();
        allFilesMarkdown.append("Project Name: ").append(project.getName()).append("\n\n");

        for (VirtualFile file : allOpenFiles) {
            try {
                String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
                allFilesMarkdown.append(formatter.formatFileContent(project, file, content));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        CopyUtil.copyToClipboardWithNotification(allFilesMarkdown.toString(), project);
    }
}
