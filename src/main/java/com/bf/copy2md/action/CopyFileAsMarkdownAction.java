package com.bf.copy2md.action;

import com.bf.copy2md.formatter.MarkdownFormatter;
import com.bf.copy2md.util.CopyUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class CopyFileAsMarkdownAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(CopyFileAsMarkdownAction.class);
    private final MarkdownFormatter formatter = new MarkdownFormatter();

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

//    @Override
//    public void update(@NotNull AnActionEvent e) {
//        LOG.info("CopyFileAsMarkdownAction.update called");
//
//        // 始终设置为可见
//        e.getPresentation().setVisible(true);
//
//        Project project = e.getProject();
//        if (project == null) {
//            LOG.info("Project is null");
//            e.getPresentation().setEnabled(false);
//            return;
//        }
//
//        // 获取当前上下文
//        String place = e.getPlace();
//        LOG.info("Action place: " + place);
//
//        boolean enabled = false;
//
//        // 处理项目视图中的右键菜单
//        if (ActionPlaces.PROJECT_VIEW_POPUP.equals(place)) {
//            // 1. 尝试多种方式获取选中的文件
//            VirtualFile[] files = null;
//
//            // 从CommonDataKeys获取
//            files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
//            LOG.info("Trying CommonDataKeys.VIRTUAL_FILE_ARRAY: " + (files != null ? files.length : "null"));
//
//            // 如果上面方法失败，尝试获取PSI元素
//            if (files == null || files.length == 0) {
//                PsiElement[] psiElements = e.getData(LangDataKeys.PSI_ELEMENT_ARRAY);
//                if (psiElements != null && psiElements.length > 0) {
//                    List<VirtualFile> fileList = new ArrayList<>();
//                    for (PsiElement element : psiElements) {
//                        if (element instanceof PsiFile) {
//                            VirtualFile vFile = ((PsiFile) element).getVirtualFile();
//                            if (vFile != null) {
//                                fileList.add(vFile);
//                            }
//                        }
//                    }
//                    if (!fileList.isEmpty()) {
//                        files = fileList.toArray(new VirtualFile[0]);
//                    }
//                }
//                LOG.info("Trying PSI_ELEMENT_ARRAY: " + (files != null ? files.length : "null"));
//            }
//
//            // 尝试获取单个文件
//            if (files == null || files.length == 0) {
//                VirtualFile singleFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
//                if (singleFile != null) {
//                    files = new VirtualFile[]{singleFile};
//                }
//                LOG.info("Trying CommonDataKeys.VIRTUAL_FILE: " + (singleFile != null));
//            }
//
//            // 检查获取到的文件
//            if (files != null && files.length > 0) {
//                LOG.info("Found " + files.length + " files");
//                for (VirtualFile file : files) {
//                    if (file != null) {
//                        LOG.info("Processing file: " + file.getPath() +
//                                ", isDirectory: " + file.isDirectory() +
//                                ", exists: " + file.exists());
//                        if (!file.isDirectory()) {
//                            enabled = true;
//                            break;
//                        }
//                    }
//                }
//            } else {
//                LOG.info("No files found in project view");
//            }
//        }
//        // 处理编辑器中的右键菜单
//        else if (ActionPlaces.EDITOR_POPUP.equals(place)) {
//            Editor editor = e.getData(CommonDataKeys.EDITOR);
//            VirtualFile file = null;
//            if (editor != null) {
//                file = FileDocumentManager.getInstance().getFile(editor.getDocument());
//            }
//            if (file == null) {
//                file = e.getData(CommonDataKeys.VIRTUAL_FILE);
//            }
//
//            LOG.info("Editor popup - Editor: " + (editor != null));
//            LOG.info("Editor popup - VirtualFile: " + (file != null ? file.getPath() : "null"));
//
//            enabled = file != null;
//        }
//        // 处理编辑器标签页的右键菜单
//        else if (ActionPlaces.EDITOR_TAB_POPUP.equals(place)) {
//            VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
//            LOG.info("Editor tab file: " + (file != null ? file.getPath() : "null"));
//            enabled = file != null;
//        }
//
//        // 设置菜单项状态
//        e.getPresentation().setEnabled(enabled);
//        LOG.info("Menu item enabled: " + enabled);
//    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        LOG.info("CopyFileAsMarkdownAction.actionPerformed called");

        Project project = e.getRequiredData(CommonDataKeys.PROJECT);
        List<VirtualFile> filesToProcess = new ArrayList<>();

        // 获取当前上下文
        String place = e.getPlace();

        // 根据不同上下文获取目标文件
        if (ActionPlaces.PROJECT_VIEW_POPUP.equals(place)) {
            // 处理项目视图中选中的多个文件
            VirtualFile[] selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
            if (selectedFiles != null) {
                for (VirtualFile file : selectedFiles) {
                    if (!file.isDirectory()) {
                        filesToProcess.add(file);
                        LOG.info("Added file to process: " + file.getPath());
                    } else {
                        LOG.info("Skipped directory: " + file.getPath());
                    }
                }
            }
        } else if (ActionPlaces.EDITOR_POPUP.equals(place)) {
            // 处理编辑器中的当前文件
            VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
            if (file != null) {
                filesToProcess.add(file);
                LOG.info("Added current editor file: " + file.getPath());
            }
        } else if (ActionPlaces.EDITOR_TAB_POPUP.equals(place)) {
            // 处理编辑器标签页的文件
            VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
            if (file != null && !file.isDirectory()) {
                filesToProcess.add(file);
                LOG.info("Added editor tab file: " + file.getPath());
            }
        }

        if (filesToProcess.isEmpty()) {
            LOG.warn("No files to process");
            return;
        }

        try {
            StringBuilder markdown = new StringBuilder();
            markdown.append("# Project Name: ").append(project.getName()).append("\n\n");

            for (VirtualFile file : filesToProcess) {
                LOG.info("Processing file: " + file.getPath());
                try {
                    String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
                    markdown.append(formatter.formatFileContent(project, file, content));
                    markdown.append("\n\n");
                } catch (Exception ex) {
                    LOG.warn("Error processing file: " + file.getPath() + ", error: " + ex.getMessage());
                    CopyUtil.showErrorHint(project, "Error reading file: " + file.getName());
                }
            }

            CopyUtil.copyToClipboardWithNotification(markdown.toString(), project);
            LOG.info("Successfully copied " + filesToProcess.size() + " files to clipboard");
        } catch (Exception ex) {
            LOG.warn("Error in actionPerformed: " + ex.getMessage());
            CopyUtil.showErrorHint(project, "Error copying files: " + ex.getMessage());
        }
    }
}
