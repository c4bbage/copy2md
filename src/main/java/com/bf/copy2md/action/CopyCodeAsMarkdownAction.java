package com.bf.copy2md.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import com.bf.copy2md.formatter.MarkdownFormatter;
import com.bf.copy2md.util.CopyUtil;

public class CopyCodeAsMarkdownAction extends AnAction {
    private final MarkdownFormatter formatter = new MarkdownFormatter();

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        e.getPresentation().setEnabledAndVisible(
            editor != null && editor.getSelectionModel().hasSelection()
        );
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
        VirtualFile virtualFile = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE);
        Project project = e.getRequiredData(CommonDataKeys.PROJECT);

        String selectedText = editor.getSelectionModel().getSelectedText();
        if (selectedText == null || selectedText.isEmpty()) {
            return;
        }

        StringBuilder markdownBuilder = new StringBuilder();
        markdownBuilder.append("\n");
        markdownBuilder.append("# Project Name: ").append(project.getName()).append("\n\n");
        markdownBuilder.append(formatter.formatFileContent(project, virtualFile, selectedText));

        CopyUtil.copyToClipboardWithNotification(markdownBuilder.toString(), project);
    }
}
