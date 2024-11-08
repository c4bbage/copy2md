package bf.com.copy2md.analysis.impl;

import bf.com.copy2md.analysis.FunctionCallAnalyzer;
import bf.com.copy2md.model.ExtractionConfig;
import bf.com.copy2md.model.FunctionContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.getMethodSignature;

public class JavaFunctionCallAnalyzer implements FunctionCallAnalyzer {
    private final Project project;
    private final ExtractionConfig config;
    private final Set<String> processedMethods = new HashSet<>();

    public JavaFunctionCallAnalyzer(Project project, ExtractionConfig config) {
        this.project = project;
        this.config = config;
    }

    @Override
    public Set<FunctionContext> analyzeFunctionCalls(PsiElement element) {
        Set<FunctionContext> contexts = new LinkedHashSet<>();
        if (element instanceof PsiMethod) {
            analyzeFunctionContext((PsiMethod) element, contexts, 0);
        }
        return contexts;
    }

    private void analyzeFunctionContext(PsiMethod method, Set<FunctionContext> contexts, int depth) {
        String signature = String.valueOf(getMethodSignature(method));
        if (processedMethods.contains(signature) || depth > config.getMaxDepth()) {
            return;
        }
        processedMethods.add(signature);

        FunctionContext context = createFunctionContext(method);
        contexts.add(context);

        if (config.isIncludeImports() || depth < config.getMaxDepth()) {
            method.accept(new JavaRecursiveElementVisitor() {
                @Override
                public void visitMethodCallExpression(PsiMethodCallExpression expression) {
                    super.visitMethodCallExpression(expression);
                    PsiMethod calledMethod = expression.resolveMethod();
                    if (calledMethod != null && isProjectMethod(calledMethod)) {
                        analyzeFunctionContext(calledMethod, contexts, depth + 1);
                    }
                }
            });
        }
    }

    private FunctionContext createFunctionContext(PsiMethod method) {
        String name = method.getName();
        String fileName = method.getContainingFile().getName();
        String sourceText = extractSourceText(method);
        String packageName = ((PsiJavaFile) method.getContainingFile()).getPackageName();
        boolean isProjectMethod = isProjectMethod(method);

        return new FunctionContext(method, name, fileName, sourceText, packageName, isProjectMethod);
    }

    private String extractSourceText(PsiMethod method) {
        if (!config.isIncludeComments()) {
            // 移除注释的逻辑
            return removeComments(method.getText());
        }
        return method.getText();
    }
    private boolean isProjectMethod(PsiMethod method) {
        if (method == null) return false;
        PsiFile containingFile = method.getContainingFile();
        return containingFile != null &&
                !containingFile.getVirtualFile().getPath().contains("/vendor/");
    }

    private String removeComments(String sourceText) {
        // 简单的实现，可以根据需求改进
        return sourceText.replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", "") // 移除多行注释
                .replaceAll("//.*", ""); // 移除单行注释
    }

}