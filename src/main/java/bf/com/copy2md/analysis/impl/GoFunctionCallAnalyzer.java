package bf.com.copy2md.analysis.impl;

import bf.com.copy2md.analysis.FunctionCallAnalyzer;
import bf.com.copy2md.model.ExtractionConfig;
import bf.com.copy2md.model.FunctionContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public class GoFunctionCallAnalyzer implements FunctionCallAnalyzer {
    private static final Logger LOG = Logger.getInstance(GoFunctionCallAnalyzer.class);

    private final Project project;
    private final ExtractionConfig config;
    private final Set<String> processedFunctions = new HashSet<>();

    public GoFunctionCallAnalyzer(Project project, ExtractionConfig config) {
        this.project = project;
        this.config = config;
    }

    @Override
    public Set<FunctionContext> analyzeFunctionCalls(PsiElement element) {
        Set<FunctionContext> contexts = new LinkedHashSet<>();
        if (isGoFunction(element)) {
            analyzeGoFunction(element, contexts, 0);
        }
        return contexts;
    }

    private void analyzeGoFunction(PsiElement function, Set<FunctionContext> contexts, int depth) {
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

    private boolean isGoFunction(PsiElement element) {
        // 检查是否是Go函数定义
        String text = element.getText();
        return text.startsWith("func ") && text.contains("(") && text.contains(")");
    }

    private boolean isValidFunction(PsiElement function) {
        VirtualFile virtualFile = function.getContainingFile().getVirtualFile();
        return virtualFile != null &&
                !virtualFile.getPath().contains("/vendor/") &&
                !virtualFile.getPath().contains("/pkg/mod/");
    }

    private String getFunctionSignature(PsiElement function) {
        // 获取Go函数签名
        String functionText = function.getText();
        int bodyStart = functionText.indexOf("{");
        return bodyStart > 0 ? functionText.substring(0, bodyStart).trim() : functionText;
    }

    private boolean isRelevantFunction(PsiElement function) {
        // 排除测试函数
        String functionText = function.getText().toLowerCase();
        if (!config.isIncludeTests() &&
                (functionText.contains("func test") ||
                        functionText.contains("func benchmark"))) {
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
        int startIndex = functionText.indexOf("func ") + 5;
        int endIndex = functionText.indexOf("(");

        // 处理方法（带接收者的函数）
        int receiverEnd = functionText.indexOf(")");
        if (receiverEnd > 0 && receiverEnd < endIndex) {
            startIndex = receiverEnd + 1;
            while (Character.isWhitespace(functionText.charAt(startIndex))) {
                startIndex++;
            }
        }

        return functionText.substring(startIndex, endIndex).trim();
    }

    private String extractSourceText(PsiElement function) {
        if (!config.isIncludeComments()) {
            return removeGoComments(function.getText());
        }
        return function.getText();
    }

    private String extractPackageName(PsiElement function) {
        // 从go.mod文件或目录结构推断包名
        PsiFile containingFile = function.getContainingFile();
        String path = containingFile.getVirtualFile().getPath();
        String[] parts = path.split("/");

        StringBuilder packageName = new StringBuilder();
        boolean foundGoModule = false;

        for (String part : parts) {
            if (part.endsWith(".go")) {
                break;
            }
            if (foundGoModule) {
                if (packageName.length() > 0) {
                    packageName.append("/");
                }
                packageName.append(part);
            }
            if (part.equals("src") || part.equals("internal")) {
                foundGoModule = true;
            }
        }

        return packageName.toString();
    }

    private boolean isProjectFunction(PsiElement function) {
        VirtualFile virtualFile = function.getContainingFile().getVirtualFile();
        return virtualFile != null &&
                !virtualFile.getPath().contains("/vendor/") &&
                !virtualFile.getPath().contains("/pkg/mod/");
    }

    private void analyzeFunctionCalls(PsiElement function, Set<FunctionContext> contexts, int depth) {
        // 遍历函数体查找函数调用
        function.acceptChildren(new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (isGoFunctionCall(element)) {
                    PsiElement calledFunction = resolveFunction(element);
                    if (calledFunction != null) {
                        analyzeGoFunction(calledFunction, contexts, depth + 1);
                    }
                }
                element.acceptChildren(this);
            }
        });
    }

    private boolean isGoFunctionCall(PsiElement element) {
        String text = element.getText();
        return text.contains("(") && !text.startsWith("func ");
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

            // 首先在当前包中查找
            PsiElement[] functions = findFunctionsInPackage(currentFile, callName);
            if (functions.length > 0) {
                return functions[0];
            }

            // 在导入的包中查找
            return findFunctionInImports(callExpression, callName);
        } catch (Exception e) {
            LOG.warn("Error resolving Go function: " + e.getMessage());
            return null;
        }
    }

    private String extractCallName(PsiElement callExpression) {
        String text = callExpression.getText();
        int parenIndex = text.indexOf('(');
        if (parenIndex > 0) {
            // 处理包限定的函数调用 (例如: pkg.Function())
            String name = text.substring(0, parenIndex).trim();
            int lastDot = name.lastIndexOf('.');
            return lastDot > 0 ? name.substring(lastDot + 1) : name;
        }
        return null;
    }

    private PsiElement[] findFunctionsInPackage(PsiFile file, String functionName) {
        List<PsiElement> functions = new ArrayList<>();

        // 获取同一包下的所有Go文件
        VirtualFile dir = file.getVirtualFile().getParent();
        if (dir != null) {
            for (VirtualFile child : dir.getChildren()) {
                if (child.getExtension() != null && child.getExtension().equals("go")) {
                    PsiFile goFile = PsiManager.getInstance(project).findFile(child);
                    if (goFile != null) {
                        goFile.accept(new PsiRecursiveElementVisitor() {
                            @Override
                            public void visitElement(@NotNull PsiElement element) {
                                if (isGoFunction(element)) {
                                    String name = extractFunctionName(element);
                                    if (functionName.equals(name)) {
                                        functions.add(element);
                                    }
                                }
                                super.visitElement(element);
                            }
                        });
                    }
                }
            }
        }

        return functions.toArray(new PsiElement[0]);
    }

    private PsiElement findFunctionInImports(PsiElement callExpression, String functionName) {
        PsiFile file = callExpression.getContainingFile();

        // 获取所有导入声明
        List<PsiElement> imports = new ArrayList<>();
        file.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element.getText().startsWith("import")) {
                    imports.add(element);
                }
                super.visitElement(element);
            }
        });

        // 遍历导入的包
        for (PsiElement importElement : imports) {
            // 解析导入的包路径
            String importPath = extractImportPath(importElement);
            if (importPath != null) {
                // 在项目的GOPATH中查找包
                VirtualFile packageDir = findPackageDir(importPath);
                if (packageDir != null) {
                    PsiElement function = findFunctionInPackageDir(packageDir, functionName);
                    if (function != null) {
                        return function;
                    }
                }
            }
        }

        return null;
    }

    private String extractImportPath(PsiElement importElement) {
        String text = importElement.getText();
        int startQuote = text.indexOf('"');
        int endQuote = text.lastIndexOf('"');
        if (startQuote >= 0 && endQuote > startQuote) {
            return text.substring(startQuote + 1, endQuote);
        }
        return null;
    }

    private VirtualFile findPackageDir(String importPath) {
        // 在GOPATH中查找包目录
        String goPath = System.getenv("GOPATH");
        if (goPath != null) {
            File packageDir = new File(goPath + "/src/" + importPath);
            if (packageDir.exists() && packageDir.isDirectory()) {
                return LocalFileSystem.getInstance().findFileByIoFile(packageDir);
            }
        }
        return null;
    }

    private PsiElement findFunctionInPackageDir(VirtualFile packageDir, String functionName) {
        for (VirtualFile file : packageDir.getChildren()) {
            if (file.getExtension() != null && file.getExtension().equals("go")) {
                PsiFile goFile = PsiManager.getInstance(project).findFile(file);
                if (goFile != null) {
                    PsiElement[] functions = findFunctionsInFile(goFile, functionName);
                    if (functions.length > 0) {
                        return functions[0];
                    }
                }
            }
        }
        return null;
    }
    private PsiElement[] findFunctionsInFile(PsiFile goFile, String functionName) {
        List<PsiElement> functions = new ArrayList<>();

        // 遍历文件中的所有元素
        goFile.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (isGoFunction(element)) {
                    String name = extractFunctionName(element);
                    // 检查函数名是否匹配
                    if (functionName.equals(name)) {
                        functions.add(element);
                    }
                }
                super.visitElement(element);
            }
        });

        return functions.toArray(new PsiElement[0]);
    }
    private String removeGoComments(String sourceText) {
        // 移除Go风格的注释
        StringBuilder result = new StringBuilder();
        String[] lines = sourceText.split("\n");

        boolean inMultilineComment = false;

        for (String line : lines) {
            if (inMultilineComment) {
                int endComment = line.indexOf("*/");
                if (endComment >= 0) {
                    inMultilineComment = false;
                    line = line.substring(endComment + 2);
                } else {
                    continue;
                }
            }

            int startComment = line.indexOf("/*");
            if (startComment >= 0) {
                int endComment = line.indexOf("*/", startComment + 2);
                if (endComment >= 0) {
                    line = line.substring(0, startComment) +
                            line.substring(endComment + 2);
                } else {
                    line = line.substring(0, startComment);
                    inMultilineComment = true;
                }
            }

            // 移除单行注释
            int lineComment = line.indexOf("//");
            if (lineComment >= 0) {
                line = line.substring(0, lineComment);
            }

            if (!line.trim().isEmpty()) {
                result.append(line).append("\n");
            }
        }

        return result.toString();
    }

    private boolean shouldAnalyzeDeeper(int depth) {
        return depth < config.getMaxDepth();
    }
}