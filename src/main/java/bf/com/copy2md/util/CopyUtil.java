package bf.com.copy2md.util;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;

import java.awt.datatransfer.StringSelection;

public class CopyUtil {
    public static void copyToClipboardWithNotification(String content, Project project) {
        CopyPasteManager.getInstance().setContents(new StringSelection(content));
        NotificationUtil.showInfo(project, "Content copied to clipboard");
    }
}