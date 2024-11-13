package bf.com.copy2md.analysis.impl;

import bf.com.copy2md.analysis.FunctionCallAnalyzer;
import bf.com.copy2md.model.ExtractionConfig;
import bf.com.copy2md.model.FunctionContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.openapi.module.ModuleUtil;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.getMethodSignature;

public class JavaFunctionCallAnalyzer implements FunctionCallAnalyzer {
    private static final Logger LOG = Logger.getInstance(JavaFunctionCallAnalyzer.class);

    private final Project project;
    private final ExtractionConfig config;
    private final Set<String> processedMethods = new HashSet<>();
    private final String basePackage;
    private final Set<PsiMethod> staticMethodReferences = new HashSet<>();//增加静态方法引用收集器


    public JavaFunctionCallAnalyzer(Project project, ExtractionConfig config) {
        this.project = project;
        this.config = config;
        this.basePackage = determineBasePackage(project);
        LOG.info("Determined base package: " + basePackage);
    }

    @Override
    public Set<FunctionContext> analyzeFunctionCalls(PsiElement element) {
        Set<FunctionContext> contexts = new LinkedHashSet<>();
        if (element instanceof PsiMethod) {
            analyzeFunctionContext((PsiMethod) element, contexts, 0);
        }
        return contexts;
    }
    //  在现有visitor基础上扩展
    private class MethodCallVisitor extends JavaRecursiveElementVisitor {
        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            PsiMethod method = expression.resolveMethod();
            if (method != null && method.hasModifierProperty(PsiModifier.STATIC)) {
                staticMethodReferences.add(method);
            }
        }
    }
    private void analyzeFunctionContext(PsiMethod method, Set<FunctionContext> contexts, int depth) {
        if (method == null || !isValidMethod(method)) {
            return;
        }

        String signature = String.valueOf(getMethodSignature(method));
        if (processedMethods.contains(signature) || depth > config.getMaxDepth()) {
            return;
        }

        processedMethods.add(signature);
        if (isRelevantMethod(method)) {
            FunctionContext context = createFunctionContext(method);
            contexts.add(context);

            if (shouldAnalyzeDeeper(depth)) {
                analyzeMethodCalls(method, contexts, depth);
            }
        }
    }
    private String extractSourceText(PsiMethod method) {
        if (!config.isIncludeComments()) {
            // 移除注释的逻辑
            return removeComments(method.getText());
        }
        return method.getText();
    }
    private FunctionContext createFunctionContext(PsiMethod method) {
        String name = method.getName();
        String fileName = method.getContainingFile().getName();
        String sourceText = extractSourceText(method);
        String packageName = ((PsiJavaFile) method.getContainingFile()).getPackageName();
        boolean isProjectMethod = isProjectMethod(method);

        return new FunctionContext(method, name, fileName, sourceText, packageName, isProjectMethod);
    }
    private boolean shouldAnalyzeDeeper(int depth) {
        return config.isIncludeImports() || depth < config.getMaxDepth();
    }

    private void analyzeMethodCalls(PsiMethod method, Set<FunctionContext> contexts, int depth) {
        
// 原有的方法调用分析保持不变
        method.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitMethodCallExpression(PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                PsiMethod calledMethod = expression.resolveMethod();
                if (calledMethod != null) {
                    if (isProjectMethod(calledMethod)) {
                        analyzeFunctionContext(calledMethod, contexts, depth + 1);
                    } else if (isSignificantStaticMethod(calledMethod)) {
                        staticMethodReferences.add(calledMethod);
                    }
                }
            }
        });

        // 处理收集到的静态方法
        for (PsiMethod staticMethod : staticMethodReferences) {
            if (!processedMethods.contains(String.valueOf(getMethodSignature(staticMethod)))) {
                analyzeFunctionContext(staticMethod, contexts, depth + 1);
            }
        }
    }
    private boolean shouldProcessMethod(PsiMethod method) {
        return method != null
                && !processedMethods.contains(method)
                && isInProjectSource(method)
                && (method.hasModifierProperty(PsiModifier.STATIC)
                || !isUtilityMethod(method));
    }
    private boolean isSignificantStaticMethod(PsiMethod method) {
        if (!method.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
        }

        // 检查方法的复杂度和业务相关性
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return false;
        }

        // 方法体语句数超过1条才考虑
        if (body.getStatements().length <= 1) {
            return false;
        }

        // 检查是否包含重要的业务逻辑
        final boolean[] hasSignificantLogic = {false};
        body.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitMethodCallExpression(PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                // 如果调用了其他方法，认为是重要逻辑
                hasSignificantLogic[0] = true;
            }

            @Override
            public void visitIfStatement(PsiIfStatement statement) {
                super.visitIfStatement(statement);
                // 包含条件判断，认为是重要逻辑
                hasSignificantLogic[0] = true;
            }

            @Override
            public void visitForStatement(PsiForStatement statement) {
                super.visitForStatement(statement);
                // 包含循环，认为是重要逻辑
                hasSignificantLogic[0] = true;
            }
        });

        return hasSignificantLogic[0];
    }
    private boolean isInProjectSource(PsiMethod method) {
        String className = method.getContainingClass() != null ?
                method.getContainingClass().getQualifiedName() : "";
        return className != null &&
                className.startsWith(basePackage);
    }
    @NotNull
    private String determineBasePackage(Project project) {
        // 1. 从源码目录识别
        String sourcePackage = findBasePackageFromSource(project);
        if (sourcePackage != null) {
            return sourcePackage;
        }

        // 2. 从项目结构识别
        String projectPackage = findBasePackageFromProject(project);
        if (projectPackage != null) {
            return projectPackage;
        }

        // 3. 使用通用包名识别策略
        return findCommonPackagePrefix(project);
    }

    @Nullable
    private String findBasePackageFromSource(Project project) {
        VirtualFile[] sourceRoots = ProjectRootManager.getInstance(project)
                .getContentSourceRoots();

        for (VirtualFile root : sourceRoots) {
            if (!root.getPath().contains("/test/")) {
                Optional<String> basePackage = findBasePackageFromSourceRoot(root);
                if (basePackage.isPresent()) {
                    return basePackage.get();
                }
            }
        }
        return null;
    }

    private Optional<String> findBasePackageFromSourceRoot(VirtualFile root) {
        Queue<VirtualFile> queue = new LinkedList<>();
        queue.offer(root);

        while (!queue.isEmpty()) {
            VirtualFile current = queue.poll();
            if (!current.isDirectory()) {
                continue;
            }

            for (VirtualFile child : current.getChildren()) {
                if (child.getName().endsWith(".java")) {
                    String packageName = extractPackageName(child);
                    if (packageName != null && !packageName.isEmpty()) {
                        return Optional.of(packageName);
                    }
                }
                if (child.isDirectory()) {
                    queue.offer(child);
                }
            }
        }
        return Optional.empty();
    }

    @Nullable
    private String extractPackageName(VirtualFile javaFile) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(javaFile);
        if (psiFile instanceof PsiJavaFile) {
            return ((PsiJavaFile) psiFile).getPackageName();
        }
        return null;
    }

    @Nullable
    private String findBasePackageFromProject(Project project) {
        Module[] modules = ModuleManager.getInstance(project).getModules();
        if (modules.length > 0) {
            // 获取主模块的包名
            Module mainModule = modules[0];
            VirtualFile[] sourceRoots = ModuleRootManager.getInstance(mainModule).getSourceRoots();
            if (sourceRoots.length > 0) {
                return extractPackageFromSourceRoot(sourceRoots[0]);
            }
        }
        return null;
    }


    private String extractPackageFromSourceRoot(VirtualFile sourceRoot) {
        PsiDirectory directory = PsiManager.getInstance(project).findDirectory(sourceRoot);
        if (directory != null) {
            PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(directory);
            if (psiPackage != null) {
                return psiPackage.getQualifiedName();
            }
        }
        return null;
    }

    private String findCommonPackagePrefix(Project project) {
        Set<String> allPackages = collectAllPackages(project);
        if (allPackages.isEmpty()) {
            return "";
        }
        else {
            return allPackages.stream()
                    .filter(pkg -> !pkg.isEmpty())
                    .min(Comparator.comparingInt(String::length))
                    .orElse("");
        }
//        List<String> sortedPackages = new ArrayList<>(allPackages);
//        Collections.sort(sortedPackages);
//
//        String firstPackage = sortedPackages.get(0);
//        if (sortedPackages.size() == 1) {
//            return firstPackage;
//        }
//
//        return findLongestCommonPrefix(sortedPackages);
    }


    private Set<String> collectAllPackages(Project project) {
        Set<String> packages = new HashSet<>();
        ProjectFileIndex.getInstance(project).iterateContent(fileOrDir -> {
            if (!fileOrDir.isDirectory() && fileOrDir.getName().endsWith(".java")) {
                String packageName = extractPackageName(fileOrDir);
                if (packageName != null && !packageName.isEmpty()
                        && !packageName.contains(".test.")
                        && !packageName.contains(".tests.")) {
                    packages.add(packageName);
                }
            }
            return true;
        });
        return packages;
    }
    private String findLongestCommonPrefix(List<String> packages) {
        String first = packages.get(0);
        String last = packages.get(packages.size() - 1);
        int minLength = Math.min(first.length(), last.length());

        int i;
        for (i = 0; i < minLength; i++) {
            if (first.charAt(i) != last.charAt(i)) {
                break;
            }
        }

        String prefix = first.substring(0, i);
        return prefix.substring(0, prefix.lastIndexOf('.') + 1);
    }

    private boolean isValidMethod(PsiMethod method) {
        return method.getContainingFile() != null &&
                method.getContainingClass() != null &&
                !method.getContainingFile().getVirtualFile().getPath().contains("/vendor/");
    }


    private boolean isProjectMethod(PsiMethod method) {
        if (method == null) return false;
        PsiFile containingFile = method.getContainingFile();
        return containingFile != null &&
                !containingFile.getVirtualFile().getPath().contains("/vendor/");
    }
    private String getPackageName(PsiMethod method) {
        PsiFile file = method.getContainingFile();
        return file instanceof PsiJavaFile ? ((PsiJavaFile) file).getPackageName() : "";
    }




    private boolean isTestMethod(PsiMethod method) {
        return Arrays.stream(method.getAnnotations())
                .anyMatch(annotation -> {
                    String name = annotation.getQualifiedName();
                    return name != null && (
                            name.contains("Test") ||
                                    name.contains("Before") ||
                                    name.contains("After")
                    );
                });
    }

//    private boolean isRelevantMethod(@NotNull PsiMethod method) {
//        // 1. 检查是否是项目代码
//        String packageName = getPackageName(method);
//        if (!packageName.startsWith(basePackage)) {
//            return false;
//        }
//
//        // 2. 排除简单工具方法
//        if (isUtilityMethod(method)) {
//            return false;
//        }
//
//        // 3. 排除测试代码
//        if (!config.isIncludeTests() && isTestMethod(method)) {
//            return false;
//        }
//
//        return true;
//    }
    //
private boolean isRelevantMethod(@NotNull PsiMethod method) {
    // 1. 检查是否在项目源码目录下
    VirtualFile virtualFile = method.getContainingFile().getVirtualFile();
    boolean isInSourceRoot = ProjectRootManager.getInstance(project)
            .getFileIndex().isInSourceContent(virtualFile);

    if (!isInSourceRoot) {
        return false;
    }

    // 2. 排除简单工具方法
    if (isUtilityMethod(method)) {
        return false;
    }

    // 3. 排除测试代码
    if (!config.isIncludeTests() && isTestMethod(method)) {
        return false;
    }

    return true;
}
    private boolean isUtilityMethod(PsiMethod method) {
        String name = method.getName();

        // 排除 getter/setter
        if ((name.startsWith("get") || name.startsWith("set")) &&
                name.length() > 3 && Character.isUpperCase(name.charAt(3))) {
            return true;
        }

        // 排除 Object 类的基础方法
        if (name.equals("toString") || name.equals("hashCode") ||
                name.equals("equals") || name.equals("clone")) {
            return true;
        }

        // 检查方法体复杂度
        PsiCodeBlock body = method.getBody();
        return body != null && body.getStatements().length <= 1;
    }

    private String removeComments(String sourceText) {
        // 简单的实现，可以根据需求改进
        return sourceText.replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", "") // 移除多行注释
                         .replaceAll("//.*", "") // 移除单行注释
                         .replaceAll("(?m)^\\s*\n|\\s+$", ""); // 移除空行
    }
}