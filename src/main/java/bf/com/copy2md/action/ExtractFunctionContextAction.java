package bf.com.copy2md.action;

import bf.com.copy2md.analysis.FunctionCallAnalyzer;
//import bf.com.copy2md.analysis.impl.JavaFunctionCallAnalyzer;
//import bf.com.copy2md.analysis.impl.PythonFunctionCallAnalyzer;
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
import java.util.List;
import java.util.Set;

public class ExtractFunctionContextAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getRequiredData(CommonDataKeys.PROJECT);
        Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getRequiredData(CommonDataKeys.PSI_FILE);

        try {
            PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
            PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);

            if (method != null) {
                ExtractionConfig config = ExtractionConfig.builder()
                        .includeComments(false)
                        .includeImports(true)
                        .maxDepth(1)
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
        //// Python示例
        //        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        //        if (psiFile != null) {
        //            PythonFunctionCallAnalyzer analyzer = new PythonFunctionCallAnalyzer(psiFile);
        //            List<PythonFunctionCallAnalyzer.FunctionInfo> functions = analyzer.analyze();
        //            for (PythonFunctionCallAnalyzer.FunctionInfo function : functions) {
        //                String markdown = analyzer.convertToMarkdown(function);
        //                // 处理markdown输出...
        //            }
        //        }
        //
        //// Go示例
        //        GoFile goFile = e.getData(CommonDataKeys.PSI_FILE);
        //        if (goFile != null) {
        //            GoFunctionCallAnalyzer analyzer = new GoFunctionCallAnalyzer(goFile);
        //            List<GoFunctionInfo> functions = analyzer.analyze();
        //            for (GoFunctionInfo function : functions) {
        //                String markdown = analyzer.convertToMarkdown(function);
        //                // 处理markdown输出...
        //            }
        //        }
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