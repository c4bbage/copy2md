package bf.com.copy2md.analysis.impl;

import bf.com.copy2md.analysis.FunctionCallAnalyzer;
import bf.com.copy2md.model.ExtractionConfig;
import bf.com.copy2md.model.FunctionContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
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
    private void collectPythonFiles(VirtualFile dir, List<VirtualFile> pythonFiles) {
        for (VirtualFile file : dir.getChildren()) {
            if (file.isDirectory()) {
                // 排除虚拟环境和缓存目录
                String name = file.getName();
                if (!name.equals("venv") && !name.equals("__pycache__") && !name.startsWith(".")) {
                    collectPythonFiles(file, pythonFiles);
                }
            } else if ("py".equals(file.getExtension())) {
                pythonFiles.add(file);
            }
        }
    }
    private Collection<VirtualFile> findProjectPythonFiles(Project project) {
        List<VirtualFile> pythonFiles = new ArrayList<>();
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir != null) {
            collectPythonFiles(baseDir, pythonFiles);
        }
        return pythonFiles;
    }

    private void initializeAllClassContexts(PsiElement element) {
        // 初始化当前文件
        initializeClassContexts(element.getContainingFile());

        // 获取项目中所有相关Python文件
        Project project = element.getProject();
        Collection<VirtualFile> pythonFiles = findProjectPythonFiles(project);

        // 初始化所有文件的类上下文
        for (VirtualFile file : pythonFiles) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile != null) {
                initializeClassContexts(psiFile);
            }
        }
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
                    processClassDefinition(element);
                }
                super.visitElement(element);
            }
        });
    }

    private void processClassDefinition(PsiElement classElement) {
        String className = extractClassName(classElement);
        ClassContext context = new ClassContext(className, classElement);

        // 收集所有方法，包括装饰器方法
        for (PsiElement child : classElement.getChildren()) {
            if (isPythonFunctionOrMethod(child)) {
                String methodName = extractFunctionName(child);
                context.methods.put(methodName, child);
            }
        }

        classContextMap.put(className, context);
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
        List<ClassContext> classStack = new ArrayList<>();
        PsiElement current = element;

        while (current != null) {
            if (isPythonClass(current)) {
                String className = extractClassName(current);
                ClassContext context = classContextMap.get(className);
                if (context != null) {
                    classStack.add(0, context);
                }
            }
            current = current.getParent();
        }

        // 返回最近的类上下文
        return classStack.isEmpty() ? null : classStack.get(0);
    }

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
        if (endIndex == -1) {
            endIndex = text.length();
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

    private PsiElement resolveMethodCall(PsiElement call) {
        String callText = call.getText();
        if (callText.startsWith("self.")) {
            // 处理self调用
            return resolveClassMethod(call, getClassContext(call));
        } else if (callText.contains(".")) {
            // 处理普通方法调用
            return resolveObjectMethod(call);
        } else {
            // 处理普通函数调用
            return resolveFunction(call);
        }
    }

    private PsiElement resolveClassMethod(PsiElement call, ClassContext context) {
        if (context == null) return null;

        String methodName = extractMethodName(call);
        return context.methods.get(methodName);
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
//                analyzeFunctionCallsInternal(function, contexts, depth, classContext);
                analyzeMethodCalls(function, contexts, depth + 1, classContext, context);
            }
        }
    }
    /**
     * 判断是否是Python方法调用
     * @param element 需要判断的元素
     * @return true表示是方法调用
     */
    private boolean isPythonMethodCall(PsiElement element) {
        if (element == null) return false;

        String text = element.getText().trim();
        // 检查是否包含方法调用的特征
        if (text.contains("(")) {
            // 1. self调用
            if (text.startsWith("self.")) return true;

            // 2. 对象方法调用
            if (text.contains(".") && !text.startsWith("def ")) return true;

            // 3. 类方法调用 (类名.方法)
            for (String className : classContextMap.keySet()) {
                if (text.startsWith(className + ".")) return true;
            }
        }

        return false;
    }

    /**
     * 解析对象方法调用
     * @param call 方法调用表达式
     * @return 解析到的方法定义元素
     */
    private PsiElement resolveObjectMethod(PsiElement call) {
        String text = call.getText();
        int dotIndex = text.lastIndexOf(".");
        if (dotIndex <= 0) return null;

        // 提取对象部分和方法名
        String objectPart = text.substring(0, dotIndex).trim();
        String methodName = extractMethodName(call);

        // 1. 尝试解析为类方法调用
        for (Map.Entry<String, ClassContext> entry : classContextMap.entrySet()) {
            if (objectPart.equals(entry.getKey())) {
                PsiElement method = entry.getValue().methods.get(methodName);
                if (method != null) return method;
            }
        }

        // 2. 尝试解析为导入对象的方法调用
        List<ImportInfo> imports = collectImports(call.getContainingFile());
        for (ImportInfo importInfo : imports) {
            if (objectPart.equals(importInfo.importedName) ||
                    objectPart.equals(importInfo.alias)) {
                return resolveImportedMethod(importInfo, methodName);
            }
        }

        return null;
    }

    /**
     * 解析导入的方法
     * @param importInfo 导入信息
     * @param methodName 方法名
     * @return 解析到的方法定义元素
     */
    private PsiElement resolveImportedMethod(ImportInfo importInfo, String methodName) {
        VirtualFile projectRoot = project.getBaseDir();
        if (projectRoot == null) return null;

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
                    // 查找类定义和方法
                    ClassContext classContext = findClassInFile(psiFile, importInfo.importedName);
                    if (classContext != null) {
                        PsiElement method = classContext.methods.get(methodName);
                        if (method != null) return method;
                    }
                }
            }
        }

        return null;
    }

    /**
     * 在文件中查找类定义
     * @param file Python文件
     * @param className 类名
     * @return 类上下文对象
     */
    private ClassContext findClassInFile(PsiFile file, String className) {
        AtomicReference<ClassContext> result = new AtomicReference<>();

        file.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (result.get() != null) return;

                if (isPythonClass(element)) {
                    String name = extractClassName(element);
                    if (className.equals(name)) {
                        ClassContext context = new ClassContext(name, element);
                        collectClassMethods(element, context);
                        result.set(context);
                    }
                }

                super.visitElement(element);
            }
        });

        return result.get();
    }

    /**
     * 从方法调用中提取方法名
     * @param call 方法调用表达式
     * @return 方法名
     */
    private String extractMethodName(PsiElement call) {
        String text = call.getText().trim();
        int dotIndex = text.lastIndexOf(".");
        int parenIndex = text.indexOf("(");

        if (dotIndex >= 0 && parenIndex > dotIndex) {
            return text.substring(dotIndex + 1, parenIndex).trim();
        }
        return null;
    }
    private void analyzeMethodCalls(PsiElement function, Set<FunctionContext> contexts,
                                    int depth, ClassContext classContext,
                                    FunctionContext parentContext) {
        // 防止递归过深
        if (depth > config.getMaxDepth()) return;

        // 获取所有方法调用
        Map<PsiElement, String> calls = new LinkedHashMap<>();
        function.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (isPythonMethodCall(element)) {
                    String signature = getFunctionSignature(element, classContext);
                    if (!processedFunctions.contains(signature)) {
                        calls.put(element, signature);
                    }
                }
                super.visitElement(element);
            }
        });

        // 分析每个调用
        for (Map.Entry<PsiElement, String> entry : calls.entrySet()) {
            PsiElement call = entry.getKey();
            String signature = entry.getValue();

            // 解析调用的函数
            PsiElement target = resolveMethodCall(call);
            if (target != null) {
                FunctionContext context = createFunctionContext(target,
                        getClassContext(target));
                contexts.add(context);
                parentContext.addDependency(context);

                // 递归分析
                analyzePythonFunction(target, contexts, depth + 1,
                        getClassContext(target));
            }
        }
    }

    private boolean isClassMethodCall(PsiElement callElement,
                                      ClassContext classContext) {
        if (classContext == null) return false;
        String text = callElement.getText();
        return text.startsWith("self.") ||
                text.startsWith(classContext.className + ".");
    }


    private void processFunctionCall(PsiElement function,
                                     Set<FunctionContext> contexts,
                                     int depth,
                                     ClassContext classContext,
                                     FunctionContext parentContext) {
        String signature = getFunctionSignature(function, classContext);
        if (processedFunctions.contains(signature)) return;

        FunctionContext context = createFunctionContext(function, classContext);
        contexts.add(context);
        parentContext.addDependency(context);

        analyzePythonFunction(function, contexts, depth, classContext);
    }

    private boolean isProcessed(PsiElement function) {
        ClassContext classContext = getClassContext(function);
        String signature = getFunctionSignature(function, classContext);
        return processedFunctions.contains(signature);
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
    private String buildFullQualifiedName(PsiElement function, ClassContext classContext) {
        StringBuilder name = new StringBuilder();

        // 添加类名前缀
        if (classContext != null) {
            name.append(classContext.className).append(".");
        }

        // 添加函数名
        name.append(extractFunctionName(function));

        return name.toString();
    }
    private FunctionContext createFunctionContext(PsiElement function, ClassContext classContext) {
        // 1. 构建完整的函数名
        String name = buildFullQualifiedName(function, classContext);

        // 2. 提取完整的源代码
        String sourceText = extractSourceText(function);

        // 3. 创建上下文对象
        return new FunctionContext(
                function,
                name,
                function.getContainingFile().getName(),
                sourceText,
                extractPackageName(function),
                isProjectFunction(function)
        );
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
        int startIndex = 0;

        // 1. 处理装饰器
        while (startIndex < functionText.length() && functionText.charAt(startIndex) == '@') {
            startIndex = functionText.indexOf("\n", startIndex) + 1;
        }

        // 2. 处理缩进
        int level = getIndentationLevel(functionText.substring(0, startIndex));
        startIndex += level * 4;

        // 3. async的情况
        if (functionText.startsWith("async ", startIndex)) {
            startIndex += 6;
        }

        // 4. def的情况
        startIndex += 3; // skip "def "
        int endIndex = functionText.indexOf("(", startIndex);
        return functionText.substring(startIndex, endIndex).trim();
    }
    private int getIndentationLevel(String text) {
        int level = 0;
        for (char c : text.toCharArray()) {
            if (c == ' ') level++;
            else if (c == '\t') level += 4;
            else break;
        }
        return level / 4;
    }
    private String extractSourceText(PsiElement function) {
        Document document = PsiDocumentManager.getInstance(project)
                .getDocument(function.getContainingFile());
        if (document == null) return function.getText();

        // 1. 获取起始位置（包括装饰器）
        PsiElement start = function;
        while (start.getPrevSibling() != null &&
                (start.getPrevSibling().getText().trim().startsWith("@") ||
                        start.getPrevSibling().getText().trim().isEmpty())) {
            start = start.getPrevSibling();
        }

        // 2. 获取函数的基础缩进级别
        int startLine = document.getLineNumber(start.getTextRange().getStartOffset());
        String firstLine = document.getText(new TextRange(
                document.getLineStartOffset(startLine),
                document.getLineEndOffset(startLine)
        ));
        int baseIndent = getIndentLevel(firstLine);

        // 3. 查找函数结束位置
        int currentLine = startLine + 1;
        int endLine = currentLine;
        int lineCount = document.getLineCount();

        while (currentLine < lineCount) {
            String line = document.getText(new TextRange(
                    document.getLineStartOffset(currentLine),
                    document.getLineEndOffset(currentLine)
            ));

            if (!line.trim().isEmpty()) {
                int indent = getIndentLevel(line);
                if (indent <= baseIndent && !line.trim().startsWith("@")) {
                    break;
                }
            }
            endLine = currentLine;
            currentLine++;
        }

        // 4. 获取完整的函数文本
        int startOffset = start.getTextRange().getStartOffset();
        int endOffset = endLine < lineCount - 1 ?
                document.getLineStartOffset(endLine + 1) :
                document.getTextLength();

        String completeText = document.getText(new TextRange(startOffset, endOffset));

        // 5. 根据配置处理注释
        return config.isIncludeComments() ?
                completeText :
                removePythonComments(completeText);
    }

    private int getIndentLevel(String line) {
        int indent = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') {
                indent++;
            } else if (c == '\t') {
                indent += 4;
            } else {
                break;
            }
        }
        return indent;
    }

    private int findFunctionEndOffset(PsiElement function, Document document) {
        int startLine = document.getLineNumber(function.getTextRange().getStartOffset());
        int baseIndent = getIndentLevel(document.getText(new TextRange(
                document.getLineStartOffset(startLine),
                document.getLineEndOffset(startLine)
        )));

        int currentLine = startLine + 1;
        int lineCount = document.getLineCount();

        while (currentLine < lineCount) {
            String line = document.getText(new TextRange(
                    document.getLineStartOffset(currentLine),
                    document.getLineEndOffset(currentLine)
            ));

            if (!line.trim().isEmpty()) {
                int indent = getIndentLevel(line);
                if (indent <= baseIndent) {
                    return document.getLineStartOffset(currentLine);
                }
            }
            currentLine++;
        }

        return document.getTextLength();
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