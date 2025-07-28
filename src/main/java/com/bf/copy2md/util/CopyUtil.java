package com.bf.copy2md.util;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import java.awt.datatransfer.StringSelection;

public class CopyUtil {
    public static void copyToClipboardWithNotification(String content, Project project) {
        CopyPasteManager.getInstance().setContents(new StringSelection(content));

        NotificationGroupManager.getInstance()
                .getNotificationGroup("Copy2MD Notification Group")
                .createNotification("Content copied to clipboard as Markdown", NotificationType.INFORMATION)
                .notify(project);
    }

    public static void showErrorHint(Project project, String message) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Copy2MD Notification Group")
                .createNotification(message, NotificationType.ERROR)
                .notify(project);
    }

    public static void showInfoNotification(Project project, String message) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Copy2MD Notification Group")
                .createNotification(message, NotificationType.INFORMATION)
                .notify(project);
    }
}