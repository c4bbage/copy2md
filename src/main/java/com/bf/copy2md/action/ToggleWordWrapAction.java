package com.bf.copy2md.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import com.bf.copy2md.util.CopyUtil;

public class ToggleWordWrapAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        e.getPresentation().setEnabledAndVisible(editor != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        Project project = e.getData(CommonDataKeys.PROJECT);

        if (editor == null || project == null) {
            return;
        }

        if (editor instanceof EditorEx) {
            EditorEx editorEx = (EditorEx) editor;

            // 获取当前软换行状态
            boolean currentWrapState = editorEx.getSettings().isUseSoftWraps();

            // 切换软换行状态
            boolean newWrapState = !currentWrapState;
            editorEx.getSettings().setUseSoftWraps(newWrapState);

            // 显示状态通知
            String message = newWrapState ? "Word wrap enabled" : "Word wrap disabled";
            CopyUtil.showInfoNotification(project, message);
        }
    }
}