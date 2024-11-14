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
    private static class ClassContext {
        final String className;
        final PsiElement classElement;
        final Map<String, PsiElement> methods;

        ClassContext(String className, PsiElement classElement) {
            this.className = className;
            this.classElement = classElement;
            this.methods = new HashMap<>();
        }
    }
    private final Map<String, ClassContext> classContextMap = new HashMap<>();

    public PythonFunctionCallAnalyzer(Project project, ExtractionConfig config) {
        this.project = project;
        this.config = config;
    }


    @Override
    public Set<FunctionContext> analyzeFunctionCalls(PsiElement element) {
        Set<FunctionContext> contexts = new LinkedHashSet<>();
        if (element != null) {
            // 清理缓存状态
            processedFunctions.clear();
            classContextMap.clear();

            // 初始化类上下文
            initializeClassContexts(element.getContainingFile());

            // 分析函数或方法
            if (isPythonFunctionOrMethod(element)) {
                ClassContext classContext = getClassContext(element);
                analyzePythonFunction(element, contexts, 0, classContext);
            }
        }
        return contexts;
    }

    // 修改原有的analyzeFunctionCalls方法名称，避免冲突
    private void analyzeFunctionCallsInternal(PsiElement function, Set<FunctionContext> contexts, int depth, ClassContext classContext) {
        function.acceptChildren(new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (isPythonFunctionCall(element)) {
                    PsiElement calledFunction = resolveFunction(element);
                    if (calledFunction != null) {
                        analyzePythonFunction(calledFunction, contexts, depth + 1, classContext);
                    }
                }
                element.acceptChildren(this);
            }
        });
    }
    private void initializeClassContexts(PsiFile file) {
        file.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (isPythonClass(element)) {
                    String className = extractClassName(element);
                    ClassContext classContext = new ClassContext(className, element);
                    // 收集类中的所有方法
                    collectClassMethods(element, classContext);
                    classContextMap.put(className, classContext);
                }
                super.visitElement(element);
            }
        });
    }

    private void collectClassMethods(PsiElement classElement, ClassContext context) {
        classElement.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (isPythonFunctionOrMethod(element)) {
                    String methodName = extractFunctionName(element);
                    context.methods.put(methodName, element);
                }
                super.visitElement(element);
            }
        });
    }

    private ClassContext getClassContext(PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (isPythonClass(current)) {
                String className = extractClassName(current);
                return classContextMap.get(className);
            }
            current = current.getParent();
        }
        return null;
    }

    private boolean isPythonFunctionOrMethod(PsiElement element) {
        if (element == null) return false;
        String text = element.getText().trim();
        return text.startsWith("def ") || text.matches("\\s*def\\s+.*");
    }

    private boolean isPythonClass(PsiElement element) {
        if (element == null) return false;
        String text = element.getText().trim();
        return text.startsWith("class ") || text.matches("\\s*class\\s+.*");
    }

    private String extractClassName(PsiElement classElement) {
        String text = classElement.getText().trim();
        int startIndex = text.indexOf("class ") + 6;
        int endIndex = text.indexOf("(");
        if (endIndex == -1) {
            endIndex = text.indexOf(":");
        }
        return text.substring(startIndex, endIndex).trim();
    }

    // 修改现有的resolveFunction方法
    private PsiElement resolveFunction(PsiElement callExpression) {
        try {
            String callName = extractCallName(callExpression);
            if (callName == null) return null;

            // 1. 检查是否是方法调用
            if (isMethodCall(callExpression)) {
                return resolveMethodCall(callExpression);
            }

            // 2. 检查本地作用域
            PsiElement localResult = resolveLocalFunction(callExpression, callName);
            if (localResult != null) return localResult;

            // 3. 检查当前文件
            PsiElement fileResult = resolveFileFunction(callExpression, callName);
            if (fileResult != null) return fileResult;

            // 4. 检查导入
            return resolveImportedFunction(callExpression, callName);

        } catch (Exception e) {
            LOG.warn("Error resolving function call: " + e.getMessage());
            return null;
        }
    }

    private boolean isMethodCall(PsiElement callExpression) {
        String text = callExpression.getText();
        return text.contains(".") && text.contains("(");
    }

    private PsiElement resolveMethodCall(PsiElement callExpression) {
        String text = callExpression.getText();
        int dotIndex = text.lastIndexOf(".");
        int parenIndex = text.indexOf("(");

        if (dotIndex > 0 && parenIndex > dotIndex) {
            String objectPart = text.substring(0, dotIndex).trim();
            String methodName = text.substring(dotIndex + 1, parenIndex).trim();

            // 查找类定义
            for (ClassContext context : classContextMap.values()) {
                PsiElement method = context.methods.get(methodName);
                if (method != null) {
                    return method;
                }
            }
        }
        return null;
    }
    private PsiElement resolveLocalFunction(PsiElement element, String name) {
        // 在当前作用域中查找函数定义
        PsiElement scope = element.getParent();
        while (scope != null && !(scope instanceof PsiFile)) {
            if (isPythonFunctionOrMethod(scope)) {
                PsiElement def = findLocalDefinition(scope, name);
                if (def != null) {
                    return def;
                }
            }
            scope = scope.getParent();
        }
        return null;
    }
    private PsiElement resolveImportedFunction(PsiElement callExpression, String callName) {
        // 获取当前文件的导入信息
        List<ImportInfo> imports = collectImports(callExpression.getContainingFile());

        // 遍历所有导入，尝试解析函数
        for (ImportInfo importInfo : imports) {
            PsiElement resolved = resolveImportedFunctionFromInfo(importInfo, callName);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private PsiElement resolveImportedFunctionFromInfo(ImportInfo importInfo, String callName) {
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
    private PsiElement resolveFileFunction(PsiElement element, String name) {
        // 在当前文件中查找函数定义
        PsiFile file = element.getContainingFile();
        PsiElement[] functions = findFunctionsInFile(file, name);
        return functions.length > 0 ? functions[0] : null;
    }
    private void analyzePythonFunction(PsiElement function, Set<FunctionContext> contexts, int depth, ClassContext classContext) {

        if (function == null || !isValidFunction(function)) {
            return;
        }

        String functionSignature = getFunctionSignature(function, classContext);
        if (processedFunctions.contains(functionSignature) || depth > config.getMaxDepth()) {
            return;
        }

        processedFunctions.add(functionSignature);

        if (isRelevantFunction(function)) {
            FunctionContext context = createFunctionContext(function, classContext);
            contexts.add(context);

            if (shouldAnalyzeDeeper(depth)) {
                analyzeFunctionCallsInternal(function, contexts, depth, classContext);
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

    private String getFunctionSignature(PsiElement function, ClassContext classContext) {
        String signature = extractFunctionName(function);
        if (classContext != null) {
            signature = classContext.className + "." + signature;
        }
        String filePath = function.getContainingFile().getVirtualFile().getPath();
        return filePath + "::" + signature;
    }

    private FunctionContext createFunctionContext(PsiElement function, ClassContext classContext) {
        String name = extractFunctionName(function);
        if (classContext != null) {
            name = classContext.className + "." + name;
        }

        String fileName = function.getContainingFile().getName();
        String sourceText = extractSourceText(function);
        String packageName = extractPackageName(function);
        boolean isProjectFunction = isProjectFunction(function);

        return new FunctionContext(function, name, fileName, sourceText, packageName, isProjectFunction);
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


    private boolean isPythonFunctionCall(PsiElement element) {
        String text = element.getText();
        return text.contains("(") && !text.startsWith("def ");
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