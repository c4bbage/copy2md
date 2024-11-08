package bf.com.copy2md.action;

import bf.com.copy2md.analysis.FunctionCallAnalyzer;
import bf.com.copy2md.analysis.impl.JavaFunctionCallAnalyzer;
import bf.com.copy2md.model.ExtractionConfig;
import bf.com.copy2md.model.FunctionContext;
import bf.com.copy2md.util.NotificationUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.util.Set;

public class ExtractFunctionContextAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
//        Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
//        PsiFile psiFile = e.getRequiredData(CommonDataKeys.PSI_FILE);
//        Project project = e.getRequiredData(CommonDataKeys.PROJECT);
//
//        // 获取当前光标位置
//        int offset = editor.getCaretModel().getOffset();
//
//        // 获取当前位置的函数
//        PsiElement element = psiFile.findElementAt(offset);
//        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
//
//        if (method != null) {
//            String methodText = method.getText();
//            // 转换为 Markdown
//            String markdown = "```java\n" + methodText + "\n```";
//
//            // 复制到剪贴板
//            CopyPasteManager.getInstance().setContents(new StringSelection(markdown));
//
//        }
            Project project = e.getRequiredData(CommonDataKeys.PROJECT);
            Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
            PsiFile psiFile = e.getRequiredData(CommonDataKeys.PSI_FILE);

            try {
                PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
                PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);

                if (method != null) {
                    ExtractionConfig config = ExtractionConfig.builder()
                            .includeComments(true)
                            .includeImports(true)
                            .maxDepth(3)
                            .build();

                    FunctionCallAnalyzer analyzer = new JavaFunctionCallAnalyzer(project, config);
                    Set<FunctionContext> contexts = analyzer.analyzeFunctionCalls(method);

                    String markdown = new bf.com.copy2md.formatter.MarkdownFormatter().format(contexts);
                    CopyPasteManager.getInstance().setContents(new StringSelection(markdown));

                    NotificationUtil.showInfo(project, "Code context copied to clipboard!");
                }
            } catch (Exception ex) {
                NotificationUtil.showError(project, "Error extracting code context: " + ex.getMessage());
            }

    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        e.getPresentation().setEnabledAndVisible(
                editor != null &&
                        psiFile != null &&
                        psiFile.getLanguage().is(JavaLanguage.INSTANCE)
        );
    }
}