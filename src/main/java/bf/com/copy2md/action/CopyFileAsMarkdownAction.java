package bf.com.copy2md.action;
import bf.com.copy2md.formatter.MarkdownFormatter;

import bf.com.copy2md.util.CopyUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
public class CopyFileAsMarkdownAction extends AnAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
    private final MarkdownFormatter formatter = new MarkdownFormatter();

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        @NotNull Project project = e.getRequiredData(CommonDataKeys.PROJECT);

        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (files == null || files.length == 0) return;

        StringBuilder markdown = new StringBuilder();
        markdown.append("Project Name: ").append(project.getName()).append("\n\n");
        for (VirtualFile file : files) {
            try {
                String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
                markdown.append(formatter.formatFileContent(project, file, content));
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }

        CopyUtil.copyToClipboardWithNotification(markdown.toString(), project);
    }

}