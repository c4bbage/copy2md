package bf.com.copy2md.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import bf.com.copy2md.formatter.MarkdownFormatter;
import bf.com.copy2md.util.CopyUtil;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CopyCodeAsMarkdownAction extends AnAction {
    private final MarkdownFormatter formatter = new MarkdownFormatter();

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
        String selectedText = editor.getSelectionModel().getSelectedText();
        if (selectedText == null || selectedText.isEmpty()) return;

        VirtualFile virtualFile = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE);
        Project project = e.getRequiredData(CommonDataKeys.PROJECT);

        StringBuilder markdownBuilder = new StringBuilder();
        markdownBuilder.append("\n"); // 添加一个空行
        // add project name
        String content = null;
        try {
            content = new String(virtualFile.contentsToByteArray(), StandardCharsets.UTF_8);

            markdownBuilder.append("# Project Name: ").append(project.getName()).append("\n\n");
            markdownBuilder.append(formatter.formatFileContent(project, virtualFile, content));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        CopyUtil.copyToClipboardWithNotification(markdownBuilder.toString(), project);
    }


    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        boolean hasSelection = editor != null && editor.getSelectionModel().hasSelection();
        // 设置为 enabled 而不仅仅是 visible
        e.getPresentation().setEnabled(hasSelection);
        e.getPresentation().setVisible(true);
    }

}