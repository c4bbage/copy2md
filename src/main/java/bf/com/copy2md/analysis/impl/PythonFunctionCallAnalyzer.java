package bf.com.copy2md.analysis.impl;

import bf.com.copy2md.analysis.FunctionCallAnalyzer;
import bf.com.copy2md.model.ExtractionConfig;
import bf.com.copy2md.model.FunctionContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class PythonFunctionCallAnalyzer implements FunctionCallAnalyzer {
    private static final Logger LOG = Logger.getInstance(PythonFunctionCallAnalyzer.class);

    private final Project project;
    private final ExtractionConfig config;
    private final Set<String> processedFunctions = new HashSet<>();

    public PythonFunctionCallAnalyzer(Project project, ExtractionConfig config) {
        this.project = project;
        this.config = config;
    }

    @Override
    public Set<FunctionContext> analyzeFunctionCalls(PsiElement element) {
        Set<FunctionContext> contexts = new LinkedHashSet<>();
        if (isPythonFunction(element)) {
            analyzePythonFunction(element, contexts, 0);
        }
        return contexts;
    }

    private void analyzePythonFunction(PsiElement function, Set<FunctionContext> contexts, int depth) {
        if (function == null || !isValidFunction(function)) {
            return;
        }

        String functionSignature = getFunctionSignature(function);
        if (processedFunctions.contains(functionSignature) || depth > config.getMaxDepth()) {
            return;
        }

        processedFunctions.add(functionSignature);

        if (isRelevantFunction(function)) {
            FunctionContext context = createFunctionContext(function);
            contexts.add(context);

            if (shouldAnalyzeDeeper(depth)) {
                analyzeFunctionCalls(function, contexts, depth);
            }
        }
    }

    private boolean isPythonFunction(PsiElement element) {
        // 检查是否是Python函数定义
        return element.getText().startsWith("def ");
    }

    private boolean isValidFunction(PsiElement function) {
        VirtualFile virtualFile = function.getContainingFile().getVirtualFile();
        return virtualFile != null &&
                !virtualFile.getPath().contains("/venv/") &&
                !virtualFile.getPath().contains("/__pycache__/");
    }

    private String getFunctionSignature(PsiElement function) {
        // 获取Python函数签名
        String functionText = function.getText();
        int endOfSignature = functionText.indexOf(":");
        return endOfSignature > 0 ? functionText.substring(0, endOfSignature) : functionText;
    }

    private boolean isRelevantFunction(PsiElement function) {
        // 排除测试函数
        String functionText = function.getText().toLowerCase();
        if (!config.isIncludeTests() &&
                (functionText.startsWith("def test_") ||
                        functionText.contains("@pytest") ||
                        functionText.contains("@unittest"))) {
            return false;
        }

        return true;
    }

    private FunctionContext createFunctionContext(PsiElement function) {
        String name = extractFunctionName(function);
        String fileName = function.getContainingFile().getName();
        String sourceText = extractSourceText(function);
        String packageName = extractPackageName(function);
        boolean isProjectFunction = isProjectFunction(function);

        return new FunctionContext(function, name, fileName, sourceText, packageName, isProjectFunction);
    }

    private String extractFunctionName(PsiElement function) {
        String functionText = function.getText();
        int startIndex = functionText.indexOf("def ") + 4;
        int endIndex = functionText.indexOf("(");
        return functionText.substring(startIndex, endIndex).trim();
    }

    private String extractSourceText(PsiElement function) {
        if (!config.isIncludeComments()) {
            return removePythonComments(function.getText());
        }
        return function.getText();
    }

    private String extractPackageName(PsiElement function) {
        // 从__init__.py文件或目录结构推断包名
        PsiFile containingFile = function.getContainingFile();
        String path = containingFile.getVirtualFile().getPath();
        String[] parts = path.split("/");

        StringBuilder packageName = new StringBuilder();
        boolean foundPythonPackage = false;

        for (String part : parts) {
            if (part.endsWith(".py")) {
                break;
            }
            if (foundPythonPackage) {
                if (packageName.length() > 0) {
                    packageName.append(".");
                }
                packageName.append(part);
            }
            if (part.equals("src") || part.equals("python")) {
                foundPythonPackage = true;
            }
        }

        return packageName.toString();
    }

    private boolean isProjectFunction(PsiElement function) {
        VirtualFile virtualFile = function.getContainingFile().getVirtualFile();
        return virtualFile != null &&
                !virtualFile.getPath().contains("/site-packages/") &&
                !virtualFile.getPath().contains("/dist-packages/");
    }

    private void analyzeFunctionCalls(PsiElement function, Set<FunctionContext> contexts, int depth) {
        // 遍历函数体查找函数调用
        function.acceptChildren(new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (isPythonFunctionCall(element)) {
                    PsiElement calledFunction = resolveFunction(element);
                    if (calledFunction != null) {
                        analyzePythonFunction(calledFunction, contexts, depth + 1);
                    }
                }
                element.acceptChildren(this);
            }
        });
    }

    private boolean isPythonFunctionCall(PsiElement element) {
        String text = element.getText();
        return text.contains("(") && !text.startsWith("def ");
    }
    private PsiElement resolveFunction(PsiElement callExpression) {
        try {
            // 获取函数调用的名称
            String callName = extractCallName(callExpression);
            if (callName == null) {
                return null;
            }

            // 获取当前文件
            PsiFile currentFile = callExpression.getContainingFile();
            if (currentFile == null) {
                return null;
            }

            // 先检查本地作用域
            PsiElement scope = callExpression.getParent();
            while (scope != null && !(scope instanceof PsiFile)) {
                if (isPythonFunction(scope)) {
                    PsiElement localDef = findLocalDefinition(scope, callName);
                    if (localDef != null) {
                        return localDef;
                    }
                }
                scope = scope.getParent();
            }

            // 在当前文件中查找
            PsiElement[] functions = findFunctionsInFile(currentFile, callName);
            if (functions.length > 0) {
                return functions[0];
            }

            // 在导入的模块中查找
            List<ImportInfo> imports = collectImports(currentFile);
            for (ImportInfo importInfo : imports) {
                PsiElement resolvedFunction = resolveImportedFunction(importInfo, callName);
                if (resolvedFunction != null) {
                    return resolvedFunction;
                }
            }

            return null;
        } catch (Exception e) {
            LOG.warn("Error resolving Python function: " + e.getMessage());
            return null;
        }
    }

    private static class ImportInfo {
        String fromModule;
        String importedName;
        String alias;

        ImportInfo(String fromModule, String importedName, String alias) {
            this.fromModule = fromModule;
            this.importedName = importedName;
            this.alias = alias;
        }
    }

    private List<ImportInfo> collectImports(PsiFile file) {
        List<ImportInfo> imports = new ArrayList<>();
        file.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                String text = element.getText();
                if (text.startsWith("from ")) {
                    // 处理 from ... import ... 语句
                    processFromImport(text, imports);
                } else if (text.startsWith("import ")) {
                    // 处理 import ... 语句
                    processImport(text, imports);
                }
                super.visitElement(element);
            }
        });
        return imports;
    }

    private void processFromImport(String importText, List<ImportInfo> imports) {
        // 移除 'from ' 前缀
        String content = importText.substring(5).trim();
        int importIndex = content.indexOf(" import ");
        if (importIndex > 0) {
            String moduleName = content.substring(0, importIndex).trim();
            String importedItems = content.substring(importIndex + 8).trim();

            // 处理多个导入项
            for (String item : importedItems.split(",")) {
                item = item.trim();
                String importedName = item;
                String alias = null;

                // 处理别名
                int asIndex = item.indexOf(" as ");
                if (asIndex > 0) {
                    importedName = item.substring(0, asIndex).trim();
                    alias = item.substring(asIndex + 4).trim();
                }

                imports.add(new ImportInfo(moduleName, importedName, alias));
            }
        }
    }

    private void processImport(String importText, List<ImportInfo> imports) {
        // 移除 'import ' 前缀
        String content = importText.substring(7).trim();

        // 处理多个导入
        for (String item : content.split(",")) {
            item = item.trim();
            String importedName = item;
            String alias = null;

            // 处理别名
            int asIndex = item.indexOf(" as ");
            if (asIndex > 0) {
                importedName = item.substring(0, asIndex).trim();
                alias = item.substring(asIndex + 4).trim();
            }

            imports.add(new ImportInfo(null, importedName, alias));
        }
    }

    private PsiElement resolveImportedFunction(ImportInfo importInfo, String callName) {
        // 获取项目根目录
        VirtualFile projectRoot = project.getBaseDir();
        if (projectRoot == null) {
            return null;
        }

        // 构建可能的模块路径
        String modulePath = importInfo.fromModule != null ?
                importInfo.fromModule.replace(".", "/") :
                importInfo.importedName.replace(".", "/");

        // 尝试不同的可能路径
        List<String> possiblePaths = Arrays.asList(
                modulePath + ".py",
                modulePath + "/__init__.py"
        );

        for (String path : possiblePaths) {
            VirtualFile moduleFile = projectRoot.findFileByRelativePath(path);
            if (moduleFile != null) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(moduleFile);
                if (psiFile != null) {
                    // 在模块文件中查找函数
                    PsiElement[] functions = findFunctionsInFile(psiFile,
                            importInfo.fromModule != null ? importInfo.importedName : callName);
                    if (functions.length > 0) {
                        return functions[0];
                    }
                }
            }
        }

        return null;
    }

    private PsiElement findLocalDefinition(PsiElement scope, String name) {
        AtomicReference<PsiElement> result = new AtomicReference<>();
        scope.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (result.get() != null) {
                    return;
                }
                if (isPythonFunction(element) &&
                        extractFunctionName(element).equals(name)) {
                    result.set(element);
                }
                super.visitElement(element);
            }
        });
        return result.get();
    }

    private String extractCallName(PsiElement callExpression) {
        String text = callExpression.getText();
        int parenIndex = text.indexOf('(');
        if (parenIndex > 0) {
            // 处理方法调用 (例如: obj.method())
            String name = text.substring(0, parenIndex).trim();
            int lastDot = name.lastIndexOf('.');
            return lastDot > 0 ? name.substring(lastDot + 1) : name;
        }
        return null;
    }

    private PsiElement[] findFunctionsInFile(PsiFile file, String functionName) {
        List<PsiElement> functions = new ArrayList<>();
        file.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (isPythonFunction(element)) {
                    String name = extractFunctionName(element);
                    if (functionName.equals(name)) {
                        functions.add(element);
                    }
                }
                super.visitElement(element);
            }
        });
        return functions.toArray(new PsiElement[0]);
    }


    private String removePythonComments(String sourceText) {
        // 移除Python风格的注释
        StringBuilder result = new StringBuilder();
        String[] lines = sourceText.split("\n");

        boolean inMultilineComment = false;

        for (String line : lines) {
            String trimmedLine = line.trim();

            // 处理多行注释
            if (trimmedLine.startsWith("'''") || trimmedLine.startsWith("\"\"\"")) {
                inMultilineComment = !inMultilineComment;
                continue;
            }

            if (!inMultilineComment) {
                // 移除单行注释
                int commentIndex = line.indexOf("#");
                if (commentIndex >= 0) {
                    line = line.substring(0, commentIndex);
                }

                if (!line.trim().isEmpty()) {
                    result.append(line).append("\n");
                }
            }
        }

        return result.toString();
    }

    private boolean shouldAnalyzeDeeper(int depth) {
        return depth < config.getMaxDepth();
    }
}