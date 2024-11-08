package bf.com.copy2md.service;

import bf.com.copy2md.model.ExtractionConfig;
import bf.com.copy2md.model.FunctionContext;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.HashSet;
import java.util.Set;

@Service(Service.Level.PROJECT)
public final class FunctionAnalyzerService {
    private final Project project;

    public FunctionAnalyzerService(Project project) {
        this.project = project;
    }

    public FunctionContext analyzeFunctionContext(PsiElement element, ExtractionConfig config) {
        return CachedValuesManager.getCachedValue(
                element,
                () -> CachedValueProvider.Result.create(
                        doAnalyze(element, config, 0, new HashSet<>()),
                        PsiModificationTracker.MODIFICATION_COUNT
                )
        );
    }

    private FunctionContext doAnalyze(PsiElement element, ExtractionConfig config,
                                      int depth, Set<String> visited) {
        if (depth > config.getMaxDepth()) {
            return createFunctionContext(element);
        }

        FunctionContext context = createFunctionContext(element);
        if (!visited.add(context.getSignature())) {
            return context;
        }

        // Find method calls within the function
        PsiTreeUtil.findChildrenOfType(element, PsiMethodCallExpression.class)
                .stream()
                .map(PsiMethodCallExpression::resolveMethod)
                .filter(method -> method != null && isProjectSource(method))
                .map(method -> doAnalyze(method, config, depth + 1, visited))
                .forEach(context::addDependency);

        return context;
    }

    private boolean isProjectSource(PsiElement element) {
        PsiFile containingFile = element.getContainingFile();
        return containingFile != null &&
                !containingFile.getVirtualFile().getPath().contains("/vendor/");
    }

    private FunctionContext createFunctionContext(PsiElement element) {
        String name = element instanceof PsiMethod ?
                ((PsiMethod) element).getName() : "unknown";
        String fileName = element.getContainingFile().getName();
        String sourceText = element.getText();
        String packageName = element.getContainingFile() instanceof PsiJavaFile ?
                ((PsiJavaFile) element.getContainingFile()).getPackageName() : "";
        boolean isProjectFunction = isProjectSource(element);

        return new FunctionContext(element, name, fileName, sourceText,
                packageName, isProjectFunction);
    }
}