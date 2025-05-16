package com.bf.copy2md.action;

import com.bf.copy2md.formatter.MarkdownFormatter;
import com.bf.copy2md.util.CopyUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode; // 用于更具体的后备处理
import com.intellij.ide.projectView.ProjectViewNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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
            LOG.info("CopyFileAsMarkdownAction.update - Place: " + e.getPlace() + ", No project found, disabling action.");
            return;
        }

        boolean hasFiles = false;
        String foundBy = "None"; // For logging which DataKey succeeded

        // Check VirtualFile array
        VirtualFile[] filesArray = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        LOG.info("VIRTUAL_FILE_ARRAY: " + (filesArray != null ? Arrays.toString(filesArray) : "null"));
        if (filesArray != null) {
            for (VirtualFile vf : filesArray) {
                LOG.info("Checking VirtualFile from VIRTUAL_FILE_ARRAY: " + (vf != null ? vf.getPath() + ", isDirectory: " + vf.isDirectory() : "null"));
                if (vf != null && !vf.isDirectory()) {
                    hasFiles = true;
                    foundBy = "VIRTUAL_FILE_ARRAY";
                    break;
                }
            }
        }

        // Check single VirtualFile
        if (!hasFiles) {
            VirtualFile singleFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
            LOG.info("VIRTUAL_FILE: " + (singleFile != null ? singleFile.getPath() + ", isDirectory: " + singleFile.isDirectory() : "null"));
            if (singleFile != null && !singleFile.isDirectory()) {
                hasFiles = true;
                foundBy = "VIRTUAL_FILE";
            }
        }

        // Check PSI elements array
        if (!hasFiles) {
            PsiElement[] psiElements = e.getData(LangDataKeys.PSI_ELEMENT_ARRAY);
            LOG.info("PSI_ELEMENT_ARRAY: " + (psiElements != null ? Arrays.toString(psiElements) : "null"));
            if (psiElements != null) {
                for (PsiElement element : psiElements) {
                    if (element instanceof PsiFile) {
                        VirtualFile vFile = ((PsiFile) element).getVirtualFile();
                        LOG.info("PSI File from PSI_ELEMENT_ARRAY: " + (vFile != null ? vFile.getPath() + ", isDirectory: " + vFile.isDirectory() : "null"));
                        if (vFile != null && !vFile.isDirectory()) {
                            hasFiles = true;
                            foundBy = "PSI_ELEMENT_ARRAY";
                            break;
                        }
                    }
                }
            }
        }

        // Check single PSI element
        if (!hasFiles) {
            PsiElement psiElement = e.getData(LangDataKeys.PSI_ELEMENT);
            LOG.info("PSI_ELEMENT: " + (psiElement != null ? psiElement.toString() : "null"));
            if (psiElement instanceof PsiFile) {
                VirtualFile vFile = ((PsiFile) psiElement).getVirtualFile();
                LOG.info("Single PSI File from PSI_ELEMENT: " + (vFile != null ? vFile.getPath() + ", isDirectory: " + vFile.isDirectory() : "null"));
                if (vFile != null && !vFile.isDirectory()) {
                    hasFiles = true;
                    foundBy = "PSI_ELEMENT";
                }
            }
        }

        // Check Navigatable Array (often useful for Project View selections)
        if (!hasFiles) {
            Object[] navigatables = e.getData(CommonDataKeys.NAVIGATABLE_ARRAY);
            LOG.info("NAVIGATABLE_ARRAY: " + (navigatables != null ? Arrays.toString(navigatables) : "null"));
            if (navigatables != null && navigatables.length > 0) {
                LOG.info("NAVIGATABLE_ARRAY has " + navigatables.length + " item(s). Iterating...");
                for (int i = 0; i < navigatables.length; i++) {
                    Object nav = navigatables[i];
                    String navClass = (nav != null ? nav.getClass().getName() : "null");
                    LOG.info("Processing NAVIGATABLE_ARRAY item #" + i + ": " + (nav != null ? nav.toString() : "null") +
                            ", Actual Class: " + navClass);

                    VirtualFile vFile = null;

                    if (nav instanceof ProjectViewNode) { // 处理 ProjectViewNode，包括 PsiFileNode
                        LOG.info("Item #" + i + " is a ProjectViewNode.");
                        ProjectViewNode<?> projectViewNode = (ProjectViewNode<?>) nav;
                        vFile = projectViewNode.getVirtualFile(); // 尝试直接获取 VirtualFile

                        // 如果直接获取的 vFile 为 null 或是目录，并且节点是 PsiFileNode，尝试通过 getValue() 获取 PsiFile
                        if ((vFile == null || (vFile != null && vFile.isDirectory())) && nav instanceof PsiFileNode) {
                            LOG.info("Item #" + i + " (ProjectViewNode) direct VirtualFile is null or directory. Trying PsiFileNode specific getValue().");
                            PsiElement element = ((PsiFileNode) nav).getValue(); // PsiFileNode.getValue() 通常返回 PsiFile
                            if (element instanceof PsiFile) {
                                LOG.info("Item #" + i + " (PsiFileNode) -> getValue() is PsiFile: " + ((PsiFile) element).getName());
                                vFile = ((PsiFile) element).getVirtualFile(); // 更新 vFile
                            } else {
                                LOG.info("Item #" + i + " (PsiFileNode) -> getValue() is not PsiFile. It's: " + (element != null ? element.getClass().getName() : "null"));
                            }
                        }

                        if (vFile != null) {
                            LOG.info("Item #" + i + " (ProjectViewNode processing) -> VirtualFile: " + vFile.getPath() + ", isDirectory: " + vFile.isDirectory() + ", isValid: " + vFile.isValid());
                        } else {
                            LOG.info("Item #" + i + " (ProjectViewNode processing) -> VirtualFile is null after attempting to get it.");
                        }

                    } else if (nav instanceof PsiFile) { // 如果不是 ProjectViewNode，但直接是 PsiFile
                        LOG.info("Item #" + i + " is PsiFile (but not a ProjectViewNode).");
                        PsiFile psiFile = (PsiFile) nav;
                        vFile = psiFile.getVirtualFile();
                        // ... （原有的日志和检查） ...
                    } else if (nav instanceof VirtualFile) { // 如果也不是 ProjectViewNode 或 PsiFile，但直接是 VirtualFile
                        LOG.info("Item #" + i + " is VirtualFile (but not a ProjectViewNode).");
                        vFile = (VirtualFile) nav;
                        // ... （原有的日志和检查） ...
                    } else {
                        LOG.warn("Item #" + i + " (" + navClass + ") is not ProjectViewNode, PsiFile, or VirtualFile. Cannot process directly as a file.");
                    }

                    // 后续的 vFile 检查逻辑保持不变
                    if (vFile != null && vFile.isValid() && !vFile.isDirectory()) {
                        LOG.info("Item #" + i + " is a valid, non-directory file. Setting hasFiles = true.");
                        hasFiles = true;
                        foundBy = "NAVIGATABLE_ARRAY";
                        break; // 找到一个文件即可
                    } else if (vFile != null && !vFile.isValid()) {
                        LOG.warn("Item #" + i + " -> VirtualFile is invalid: " + vFile.getPath());
                    } else if (vFile != null && vFile.isDirectory()) {
                        LOG.info("Item #" + i + " -> VirtualFile is a directory: " + vFile.getPath());
                    } else if (vFile == null) { // 确保在所有尝试后 vFile 仍为 null 时记录
                        LOG.info("Item #" + i + " ("+ navClass +") resulted in a null VirtualFile after all processing attempts.");
                    }
                }
            } else if (navigatables != null) { // navigatables is not null but length is 0
                LOG.info("NAVIGATABLE_ARRAY is not null but is empty.");
            }
        }

        // Check editor (less likely for ProjectViewPopup but good for completeness)
        // Only check editor if context is editor related, or if no files found yet and it's a fallback
        String place = e.getPlace();
        boolean isEditorContext = ActionPlaces.EDITOR_POPUP.equals(place) || ActionPlaces.EDITOR_TAB_POPUP.equals(place);

        if (!hasFiles && isEditorContext) { // Only try editor if no files found AND it's an editor context
            Editor editor = e.getData(CommonDataKeys.EDITOR);
            LOG.info("EDITOR: " + (editor != null ? "available" : "null (or not an editor context for this check)"));
            if (editor != null) {
                VirtualFile fileFromDoc = FileDocumentManager.getInstance().getFile(editor.getDocument());
                LOG.info("Editor File: " + (fileFromDoc != null ? fileFromDoc.getPath() + ", isDirectory: " + fileFromDoc.isDirectory() : "null"));
                if (fileFromDoc != null && !fileFromDoc.isDirectory()) {
                    hasFiles = true;
                    foundBy = "EDITOR";
                }
            }
        } else if (!hasFiles) {
            LOG.info("Skipping EDITOR check as hasFiles is already " + hasFiles + " or context is not editor-specific (" + place + ")");
        }


        e.getPresentation().setEnabledAndVisible(hasFiles);
        LOG.info("CopyFileAsMarkdownAction.update - Place: " + place + ", Enabled: " + hasFiles + (hasFiles ? " (Found by: " + foundBy + ")" : " (No suitable file found)"));
    }
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        LOG.info("CopyFileAsMarkdownAction.actionPerformed called. Place: " + e.getPlace());

        Project project = e.getRequiredData(CommonDataKeys.PROJECT);
        List<VirtualFile> filesToProcess = new ArrayList<>();

        // Try all possible sources to get files
        filesToProcess = collectFilesFromAllSources(e);

        if (filesToProcess.isEmpty()) {
            LOG.warn("No files to process after checking all sources.");
            CopyUtil.showErrorHint(project, "No file context found for copying.");
            return;
        }

        // Processing files
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
    
    /**
     * Collect files from all possible data sources in the action event
     */
    private List<VirtualFile> collectFilesFromAllSources(AnActionEvent e) {
        List<VirtualFile> files = new ArrayList<>();
        
        // Try getting virtual file array first (multiple selection)
        VirtualFile[] selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (selectedFiles != null && selectedFiles.length > 0) {
            LOG.info("Found VIRTUAL_FILE_ARRAY with " + selectedFiles.length + " items");
            for (VirtualFile file : selectedFiles) {
                if (file != null && !file.isDirectory()) {
                    files.add(file);
                    LOG.info("Added file from array: " + file.getPath());
                } else {
                    LOG.info("Skipped item from array (null or directory): " + (file != null ? file.getPath() : "null"));
                }
            }
        }

        // If we already have files, return them
        if (!files.isEmpty()) {
            return files;
        }
        
        // Try getting a single virtual file
        VirtualFile singleFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (singleFile != null && !singleFile.isDirectory()) {
            LOG.info("Found single VIRTUAL_FILE: " + singleFile.getPath());
            files.add(singleFile);
            return files;
        }
        
        // Try getting PSI elements
        PsiElement[] psiElements = e.getData(LangDataKeys.PSI_ELEMENT_ARRAY);
        if (psiElements != null && psiElements.length > 0) {
            LOG.info("Found PSI_ELEMENT_ARRAY with " + psiElements.length + " items");
            for (PsiElement element : psiElements) {
                if (element instanceof PsiFile) {
                    VirtualFile vFile = ((PsiFile) element).getVirtualFile();
                    if (vFile != null && !vFile.isDirectory()) {
                        files.add(vFile);
                        LOG.info("Added file from PSI array: " + vFile.getPath());
                    }
                }
            }
            
            if (!files.isEmpty()) {
                return files;
            }
        }
        
        // Try getting single PSI element
        PsiElement psiElement = e.getData(LangDataKeys.PSI_ELEMENT);
        if (psiElement instanceof PsiFile) {
            VirtualFile vFile = ((PsiFile) psiElement).getVirtualFile();
            if (vFile != null && !vFile.isDirectory()) {
                LOG.info("Added file from PSI_ELEMENT: " + vFile.getPath());
                files.add(vFile);
                return files;
            }
        }
        
        // Try getting editor document
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor != null) {
            VirtualFile fileFromDoc = FileDocumentManager.getInstance().getFile(editor.getDocument());
            if (fileFromDoc != null && !fileFromDoc.isDirectory()) {
                LOG.info("Added file from editor document: " + fileFromDoc.getPath());
                files.add(fileFromDoc);
                return files;
            }
        }

        // Try NAVIGATABLE_ARRAY as last resort (or based on your preferred order)
        if (files.isEmpty()) { // 仅当 'files' 列表仍为空时处理
            Object[] navigatables = e.getData(CommonDataKeys.NAVIGATABLE_ARRAY);
            if (navigatables != null && navigatables.length > 0) {
                LOG.info("Processing NAVIGATABLE_ARRAY in collectFilesFromAllSources, " + navigatables.length + " items.");
                for (Object nav : navigatables) {
                    VirtualFile vFile = null;
                    String navClass = (nav != null ? nav.getClass().getName() : "null");
                    LOG.info("Collecting from NAVIGATABLE_ARRAY item: " + (nav != null ? nav.toString() : "null") + ", Class: " + navClass);

                    if (nav instanceof ProjectViewNode) {
                        LOG.info("Item is ProjectViewNode.");
                        ProjectViewNode<?> projectViewNode = (ProjectViewNode<?>) nav;
                        vFile = projectViewNode.getVirtualFile();

                        if ((vFile == null || (vFile != null && vFile.isDirectory())) && nav instanceof PsiFileNode) {
                            LOG.info("ProjectViewNode's VirtualFile is null/directory for " + nav.toString() + ". Trying PsiFileNode.getValue().");
                            PsiElement element = ((PsiFileNode) nav).getValue();
                            if (element instanceof PsiFile) {
                                vFile = ((PsiFile) element).getVirtualFile();
                                LOG.info("PsiFileNode.getValue() gave PsiFile, VirtualFile: " + (vFile != null ? vFile.getPath() : "null"));
                            } else {
                                LOG.info("PsiFileNode.getValue() is not PsiFile: " + (element != null ? element.getClass().getName() : "null"));
                            }
                        } else if (vFile != null) {
                            LOG.info("ProjectViewNode.getVirtualFile() yielded: " + vFile.getPath());
                        } else {
                            LOG.info("ProjectViewNode.getVirtualFile() yielded null (and not a PsiFileNode for specified fallback or fallback failed).");
                        }
                    } else if (nav instanceof PsiFile) {
                        LOG.info("Item is PsiFile (but not ProjectViewNode).");
                        vFile = ((PsiFile) nav).getVirtualFile();
                    } else if (nav instanceof VirtualFile) {
                        LOG.info("Item is VirtualFile (but not ProjectViewNode).");
                        vFile = (VirtualFile) nav; // 确保这里也检查是否是目录
                    } else {
                        LOG.warn("Item (" + navClass + ") in NAVIGATABLE_ARRAY is not ProjectViewNode, PsiFile, or VirtualFile.");
                    }

                    if (vFile != null && !vFile.isDirectory()) { // 确保 VirtualFile 不是目录
                        if (!files.contains(vFile)) { // 避免重复添加
                            files.add(vFile);
                            LOG.info("Added file from NAVIGATABLE_ARRAY: " + vFile.getPath());
                        } else {
                            LOG.info("Skipped duplicate file from NAVIGATABLE_ARRAY: " + vFile.getPath());
                        }
                    } else if (vFile != null && vFile.isDirectory()) {
                        LOG.info("Skipped directory from NAVIGATABLE_ARRAY: " + vFile.getPath());
                    } else {
                        LOG.info("Navigatable item ("+ navClass +": " + nav.toString() + ") did not resolve to a usable file or was null.");
                    }
                }
            }
        }

        LOG.info("Found " + files.size() + " files to process from all sources");
        return files;
    }}
