package bf.com.copy2md.util;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;

public class NotificationUtil {
    private static final String GROUP_ID = "Copy2MD Notifications";

    public static void showInfo(Project project, String message) {
        notify(project, message, NotificationType.INFORMATION);
    }

    public static void showError(Project project, String message) {
        notify(project, message, NotificationType.ERROR);
    }

    public static void showWarning(Project project, String message) {
        notify(project, message, NotificationType.WARNING);
    }

    private static void notify(Project project, String message, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(GROUP_ID)
                .createNotification(message, type)
                .notify(project);
    }
}