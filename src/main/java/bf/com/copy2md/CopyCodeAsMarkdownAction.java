package bf.com.copy2md;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CopyCodeAsMarkdownAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
        String selectedText = editor.getSelectionModel().getSelectedText();
        if (selectedText == null || selectedText.isEmpty()) return;

        VirtualFile virtualFile = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE);
        Project project = e.getRequiredData(CommonDataKeys.PROJECT);

        String relativePath = getRelativePath(project, virtualFile);
        String fileExtension = virtualFile.getExtension();

        StringBuilder markdownBuilder = new StringBuilder();
        markdownBuilder.append("\n"); // 添加一个空行
        markdownBuilder.append("## File: ").append(relativePath).append("\n\n");
        markdownBuilder.append("```").append(fileExtension).append("\n");
        markdownBuilder.append(selectedText).append("\n");
        markdownBuilder.append("```");

        String markdown = markdownBuilder.toString();

        CopyPasteManager.getInstance().setContents(new StringSelection(markdown));
    }


    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        e.getPresentation().setVisible(editor != null && editor.getSelectionModel().hasSelection());
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