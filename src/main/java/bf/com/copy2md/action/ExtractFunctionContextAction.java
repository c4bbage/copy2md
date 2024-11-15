package bf.com.copy2md.action;

import bf.com.copy2md.analysis.impl.GoFunctionCallAnalyzer;
import bf.com.copy2md.analysis.impl.JavaFunctionCallAnalyzer;
import bf.com.copy2md.analysis.impl.PythonFunctionCallAnalyzer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import bf.com.copy2md.analysis.FunctionCallAnalyzer;
import bf.com.copy2md.model.FunctionContext;
import bf.com.copy2md.model.ExtractionConfig;
import bf.com.copy2md.util.NotificationUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.util.Set;

public class ExtractFunctionContextAction extends AnAction {
    private Logger LOG = Logger.getInstance(ExtractFunctionContextAction.class);
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getRequiredData(CommonDataKeys.PROJECT);
        Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getRequiredData(CommonDataKeys.PSI_FILE);

        try {
            PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());

            // 获取当前语言ID
            String languageId = psiFile.getLanguage().getID().toLowerCase();
            // 构建配置
            ExtractionConfig config = ExtractionConfig.builder()
                    .includeComments(false)
                    .includeImports(true)
                    .maxDepth(1)
                    .build();

            // 获取对应的分析器
            FunctionCallAnalyzer analyzer = FunctionAnalyzerFactory.createAnalyzer(
                    languageId, project, config);

            if (analyzer == null) {
                NotificationUtil.showError(project, "Unsupported language: " + languageId);
                return;
            }

            // 根据语言类型获取合适的上下文元素
            PsiElement contextElement = getContextElement(element, languageId);

            if (contextElement != null) {
                Set<FunctionContext> contexts = analyzer.analyzeFunctionCalls(contextElement);
                String markdown = new bf.com.copy2md.formatter.MarkdownFormatter().format(contexts,languageId);
                CopyPasteManager.getInstance().setContents(new StringSelection(markdown));
                NotificationUtil.showInfo(project, "Code context copied to clipboard!");
            } else {
                NotificationUtil.showError(project, "No valid function context found at cursor position");
            }
        } catch (Exception ex) {
            NotificationUtil.showError(project, "Error extracting code context: " + ex.getMessage());
        }
    }
    private PsiElement findPythonFunction(PsiElement element) {
        PsiElement current = element;
        NotificationUtil.showInfo(element.getProject(), "findPythonFunction");
        while (current != null) {
            // 检查是否是函数定义
            if (isPythonFunctionOrMethod(current)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }
//    private boolean isPythonFunctionOrMethod(PsiElement element) {
//        if (element == null) return false;
//
//        String text = element.getText().trim();
//
//        if (text.startsWith("def ") || text.matches("\\s*def\\s+.*")) {
//            // 检查是否是类方法
//            PsiElement parent = element.getParent();
//            while (parent != null) {
//                if (isPythonClass(parent)) {
//                    return true; // 是类方法
//                }
//                if (parent instanceof PsiFile) {
//                    return true; // 是普通函数
//                }
//                parent = parent.getParent();
//            }
//            return true; // 其他情况也返回true
//        }
//        return false;
//    }
private boolean isPythonFunctionOrMethod(PsiElement element) {
    if (element == null) return false;

    String text = element.getText().trim();
    // 检查装饰器
    PsiElement prevSibling = element.getPrevSibling();
    while (prevSibling != null) {
        String siblingText = prevSibling.getText().trim();
        if (siblingText.startsWith("@")) {
            // 包含装饰器的情况
            text = siblingText + "\n" + text;
        }
        prevSibling = prevSibling.getPrevSibling();
    }

    // 检查函数定义
    return text.contains("def ") || text.matches("\\s*async\\s+def\\s+.*")
            || text.matches("\\s*def\\s+.*");
}
    private boolean isPythonClass(PsiElement element) {
        String text = element.getText().trim();
        return text.startsWith("class ") || text.matches("\\s*class\\s+.*");
    }

    private PsiElement getContextElement(PsiElement element, String languageId) {
        switch (languageId) {
            case "java":
                return PsiTreeUtil.getParentOfType(element, PsiMethod.class);
            case "python":
                // 查找Python函数定义
                return findPythonFunction(element);
            case "go":
                // 查找Go函数定义
                return findGoFunction(element);
            default:
                return null;
        }
    }

    private PsiElement findGoFunction(PsiElement element) {
        // 简单的Go函数查找逻辑
        PsiElement parent = element.getParent();
        while (parent != null) {
            if (parent.getText().startsWith("func ")) {
                return parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 获取当前项目和编辑器
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        LOG.info("Updating action visibility:");
        LOG.info("Project: " + (project != null ? project.getName() : "null"));
        LOG.info("Editor: " + (editor != null ? "present" : "null"));
        LOG.info("PsiFile: " + (psiFile != null ? psiFile.getName() : "null"));
        if (editor != null) {
            VirtualFile virtualFile = FileDocumentManager.getInstance()
                    .getFile(editor.getDocument());
            if (virtualFile != null && project != null) {
                ProjectFileIndex projectFileIndex = ProjectRootManager
                        .getInstance(project).getFileIndex();
                boolean isInProject = projectFileIndex.isInContent(virtualFile);
                LOG.info("File is in project: " + isInProject);
            }
        }
        // 如果是从编辑器触发
        if (editor != null && psiFile == null) {
            // 尝试通过其他方式获取PsiFile
            psiFile = PsiDocumentManager.getInstance(project)
                    .getPsiFile(editor.getDocument());
            LOG.info("Attempted to get PsiFile through PsiDocumentManager: " +
                    (psiFile != null ? psiFile.getName() : "still null"));
        }
        // 检查文件类型
        if (editor != null) {
            VirtualFile virtualFile = FileDocumentManager.getInstance()
                    .getFile(editor.getDocument());
            if (virtualFile != null) {
                LOG.info("Virtual file: " + virtualFile.getName());
                LOG.info("File type: " + virtualFile.getFileType().getName());
            }
        }
        // 设置action的可见性
        boolean visible = project != null && editor != null && psiFile != null;
        e.getPresentation().setVisible(visible);



        if (!visible) {
            e.getPresentation().setEnabled(false);
            return;
        }

        // 检查语言支持
        String languageId = psiFile.getLanguage().getID().toLowerCase();

        boolean enabled = "java".equals(languageId) ||
                "python".equals(languageId) ||
                "go".equals(languageId);

        e.getPresentation().setEnabled(enabled);
    }
}

// 分析器工厂类
class FunctionAnalyzerFactory {
    public static FunctionCallAnalyzer createAnalyzer(
            String languageId, Project project, ExtractionConfig config) {
        switch (languageId) {
            case "java":
                return new JavaFunctionCallAnalyzer(project, config);
            case "python":
                return new PythonFunctionCallAnalyzer(project, config);
            case "go":
                return new GoFunctionCallAnalyzer(project, config);
            default:
                return null;
        }
    }
}