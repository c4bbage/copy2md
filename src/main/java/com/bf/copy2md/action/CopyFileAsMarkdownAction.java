package com.bf.copy2md.action;

import com.bf.copy2md.formatter.MarkdownFormatter;
import com.bf.copy2md.util.CopyUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
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

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }
    
        boolean hasFiles = false;
        // Check for multiple files (Project View context)
        VirtualFile[] filesArray = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (filesArray != null) {
            for (VirtualFile vf : filesArray) {
                // Ensure at least one non-directory file is selected
                if (vf != null && !vf.isDirectory()) {
                    hasFiles = true;
                    break;
                }
            }
        }
    
        // If no array, check for single file (Editor, Tab context)
        if (!hasFiles) {
            VirtualFile singleFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
            if (singleFile != null && !singleFile.isDirectory()) {
                hasFiles = true;
            }
        }
    
        // You could potentially add a check for the editor's current file
        // even if VIRTUAL_FILE isn't directly available in the event data
        // Editor editor = e.getData(CommonDataKeys.EDITOR);
        // if (!hasFiles && editor != null) {
        //    VirtualFile fileFromDoc = FileDocumentManager.getInstance().getFile(editor.getDocument());
        //    if (fileFromDoc != null && !fileFromDoc.isDirectory()) {
        //        hasFiles = true;
        //    }
        // }
    
        e.getPresentation().setEnabledAndVisible(hasFiles);
        // Use logging during development:
        // LOG.info("CopyFileAsMarkdownAction.update - Place: " + e.getPlace() + ", Enabled: " + hasFiles);
    }
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        LOG.info("CopyFileAsMarkdownAction.actionPerformed called. Place: " + e.getPlace());
    
        Project project = e.getRequiredData(CommonDataKeys.PROJECT);
        List<VirtualFile> filesToProcess = new ArrayList<>();
    
        // 1. Try getting an array of files (most common in Project View)
        VirtualFile[] selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (selectedFiles != null && selectedFiles.length > 0) {
            LOG.info("Processing VIRTUAL_FILE_ARRAY with " + selectedFiles.length + " items.");
            for (VirtualFile file : selectedFiles) {
                if (file != null && !file.isDirectory()) {
                    filesToProcess.add(file);
                    LOG.info("Added file from array: " + file.getPath());
                } else {
                    LOG.info("Skipped item from array (null or directory): " + (file != null ? file.getPath() : "null"));
                }
            }
        }
    
        // 2. If no array, try getting a single file (common in Editor, Tabs, or single selection in Project View)
        if (filesToProcess.isEmpty()) {
            VirtualFile singleFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
            if (singleFile != null && !singleFile.isDirectory()) {
                LOG.info("Processing single VIRTUAL_FILE: " + singleFile.getPath());
                filesToProcess.add(singleFile);
            } else {
                 LOG.info("VIRTUAL_FILE was null or a directory.");
                 // Optional: Try getting file from editor context as a fallback
                 Editor editor = e.getData(CommonDataKeys.EDITOR);
                 if (editor != null) {
                     VirtualFile fileFromDoc = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getFile(editor.getDocument());
                     if (fileFromDoc != null && !fileFromDoc.isDirectory()) {
                         LOG.info("Processing file from editor document: " + fileFromDoc.getPath());
                         filesToProcess.add(fileFromDoc);
                     }
                 }
            }
        }
    
    
        if (filesToProcess.isEmpty()) {
            LOG.warn("No files to process after checking all sources.");
            CopyUtil.showErrorHint(project, "No file context found for copying.");
            return;
        }
    
        // --- Rest of your processing logic ---
        try {
            StringBuilder markdown = new StringBuilder();
            markdown.append("# Project Name: ").append(project.getName()).append("\n\n");
    
            for (VirtualFile file : filesToProcess) {
                LOG.info("Processing file: " + file.getPath());
                try {
                    String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
                    markdown.append(formatter.formatFileContent(project, file, content));
                    markdown.append("\n\n"); // Keep separation between files
                } catch (Exception ex) {
                    LOG.warn("Error processing file: " + file.getPath() + ", error: " + ex.getMessage(), ex);
                    CopyUtil.showErrorHint(project, "Error reading file: " + file.getName());
                }
            }
    
            // Remove trailing newlines if any
            String resultMarkdown = markdown.toString().trim();
            if (!resultMarkdown.isEmpty()) {
                 CopyUtil.copyToClipboardWithNotification(resultMarkdown, project);
                 LOG.info("Successfully copied " + filesToProcess.size() + " files to clipboard");
            } else {
                 LOG.warn("Resulting markdown was empty.");
                 CopyUtil.showErrorHint(project, "No content generated for copying.");
            }
    
        } catch (Exception ex) {
            LOG.warn("Error in actionPerformed: " + ex.getMessage(), ex);
            CopyUtil.showErrorHint(project, "Error copying files: " + ex.getMessage());
        }
    }
}
