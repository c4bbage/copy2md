package com.bf.copy2md.core.extractors;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

public class GoExtractor extends BaseExtractor {
    private static final Pattern FUNC_PATTERN = Pattern.compile(
        "^\\s*func\\s+(\\(\\s*\\w+\\s+[\\*]?\\w+\\s*\\)\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\("
    );

    private static final Pattern FUNC_CALL_PATTERN = Pattern.compile(
        "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\("
    );

    private static final Pattern STRING_PATTERN = Pattern.compile(
        "(\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\")|" +
        "(`[^`]*`)"
    );

    private static final Pattern BUILTIN_PATTERN = Pattern.compile(
        "^(make|len|cap|new|append|copy|delete|close|complex|real|imag|panic|recover)$"
    );

    public GoExtractor(Project project) {
        super(project);
    }

    @Override
    public boolean canHandle(String fileExtension) {
        return fileExtension != null && fileExtension.equals("go");
    }

    @Override
    public String extractFunction(PsiElement element) {
        try {
            clearState();
            if (element == null) {
                LOG.warn("Null function element provided");
                return "No function element found";
            }

            StringBuilder result = new StringBuilder();
            result.append("// Project: ").append(project.getName()).append("\n");
            result.append("// File: ").append(element.getContainingFile().getVirtualFile().getPath()).append("\n\n");

            String mainFunction = extractFunctionCode(element);
            if (mainFunction.isEmpty()) {
                LOG.warn("No valid function code extracted from element");
                return "No valid function found";
            }

            String mainFunctionName = extractFunctionName(mainFunction);
            result.append(mainFunction).append("\n");

            extractDependencies(element, 0);

            // Add dependencies
            for (Map.Entry<String, String> entry : dependencies.entrySet()) {
                if (!entry.getKey().equals(mainFunctionName)) {
                    result.append("\n// Dependency: ").append(entry.getKey()).append("\n");
                    result.append(entry.getValue()).append("\n");
                }
            }

            return result.toString();
        } catch (Exception e) {
            LOG.error("Error extracting function", e);
            return "Error extracting function: " + e.getMessage();
        }
    }

    @Override
    protected String extractFunctionCode(PsiElement element) {
        if (element == null) {
            LOG.warn("Null element provided to extractFunctionCode");
            return "";
        }

        // 获取完整的函数文本
        String text = element.getText();
        if (text == null || text.isEmpty()) {
            LOG.warn("Empty text from element");
            return "";
        }

        // 确保我们有一个完整的函数定义
        if (!text.trim().startsWith("func")) {
            // 尝试查找父元素中的函数定义
            PsiElement parent = element.getParent();
            while (parent != null && !parent.getText().trim().startsWith("func")) {
                parent = parent.getParent();
            }
            if (parent != null) {
                text = parent.getText();
            } else {
                LOG.warn("Could not find function definition");
                return "";
            }
        }

        String[] lines = text.split("\n");
        StringBuilder functionCode = new StringBuilder();
        boolean inFunction = false;
        int bracketCount = 0;
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            
            // 开始函数定义
            if (!inFunction && trimmedLine.startsWith("func")) {
                inFunction = true;
                functionCode.append(line).append("\n");
                bracketCount += countBrackets(line);
                continue;
            }
            
            if (inFunction) {
                functionCode.append(line).append("\n");
                bracketCount += countBrackets(line);
                
                // 处理函数结束
                if (bracketCount <= 0 && trimmedLine.equals("}")) {
                    break;
                }
            }
        }
        
        String result = functionCode.toString().trim();
        if (result.isEmpty()) {
            LOG.warn("Extracted empty function code");
            return "";
        }

        if (debug) {
            LOG.info("Extracted function code:\n" + result);
        }
        
        return result;
    }

    private int countBrackets(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == '{') count++;
            else if (c == '}') count--;
        }
        return count;
    }

    private boolean isInterfaceMethod(PsiElement element) {
        PsiElement parent = element.getParent();
        while (parent != null) {
            String text = parent.getText().trim();
            if (text.startsWith("type") && text.contains("interface")) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private String extractReceiverType(String text) {
        java.util.regex.Matcher matcher = Pattern.compile("func\\s*\\((\\s*\\w+\\s+[*]?\\w+\\s*)\\)").matcher(text);
        if (matcher.find()) {
            String receiver = matcher.group(1).trim();
            String[] parts = receiver.split("\\s+");
            return parts[parts.length - 1].replaceAll("[*]", "");
        }
        return null;
    }

    private boolean isEmbeddedType(String typeName, PsiElement context) {
        // 在当前文件和导入中查找嵌入类型
        String fileContent = context.getContainingFile().getText();
        return Pattern.compile("type\\s+\\w+\\s+struct\\s*\\{[^}]*\\b" + typeName + "\\b[^}]*\\}").matcher(fileContent).find();
    }

    private PsiElement findEmbeddedMethod(PsiElement element, String typeName) {
        // 在项目中查找嵌入类型的方法
        String methodName = extractFunctionName(element.getText());
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<VirtualFile> files = FilenameIndex.getAllFilesByExt(project, "go", scope);
        
        for (VirtualFile file : files) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile != null) {
                Collection<PsiElement> functions = PsiTreeUtil.findChildrenOfType(psiFile, PsiElement.class);
                for (PsiElement function : functions) {
                    if (isFunctionDefinition(function)) {
                        String funcText = function.getText();
                        if (funcText.contains("func (" + typeName) && extractFunctionName(funcText).equals(methodName)) {
                            return function;
                        }
                    }
                }
            }
        }
        return null;
    }

    private String extractDeferredFunction(String line) {
        Pattern deferPattern = Pattern.compile("defer\\s+(\\w+)\\s*\\(");
        java.util.regex.Matcher matcher = deferPattern.matcher(line);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractGoroutineFunction(String line) {
        Pattern goPattern = Pattern.compile("go\\s+(\\w+)\\s*\\(");
        java.util.regex.Matcher matcher = goPattern.matcher(line);
        return matcher.find() ? matcher.group(1) : null;
    }

    @Override
    protected void extractDependencies(PsiElement element, int depth) {
        if (depth >= maxRecursionDepth) {
            log("Max recursion depth reached at: " + element.getText());
            return;
        }

        String text = element.getText();
        java.util.regex.Matcher matcher = FUNC_CALL_PATTERN.matcher(text);
        while (matcher.find()) {
            String functionName = matcher.group(1);
            
            // Skip if it's a builtin function
            if (BUILTIN_PATTERN.matcher(functionName).matches()) {
                continue;
            }

            // Skip if we already have this dependency
            if (dependencies.containsKey(functionName)) {
                continue;
            }

            // Find and extract the function definition
            PsiElement calledFunction = findFunctionDefinition(functionName);
            if (calledFunction != null) {
                String functionCode = extractFunctionCode(calledFunction);
                if (!functionCode.isEmpty()) {
                    dependencies.put(functionName, functionCode);
                    log("Found dependency: " + functionName);
                    
                    // Recursively extract dependencies from the called function
                    extractDependencies(calledFunction, depth + 1);
                }
            } else {
                log("Could not find definition for: " + functionName);
            }
        }

        // Process child elements
        for (PsiElement child : element.getChildren()) {
            extractDependencies(child, depth);
        }
    }

    @Override
    protected String extractFunctionName(String text) {
        // Remove string literals to avoid false matches
        text = STRING_PATTERN.matcher(text).replaceAll("");
        
        java.util.regex.Matcher matcher = FUNC_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(2);  // Group 2 contains the function name
        }
        return "";
    }

    @Override
    protected PsiElement findFunctionDefinition(String functionName) {
        if (definitionCache.containsKey(functionName)) {
            return definitionCache.get(functionName);
        }

        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<VirtualFile> files = FilenameIndex.getAllFilesByExt(project, "go", scope);
        
        for (VirtualFile file : files) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile != null) {
                Collection<PsiElement> functions = PsiTreeUtil.findChildrenOfType(psiFile, PsiElement.class);
                for (PsiElement function : functions) {
                    if (isFunctionDefinition(function)) {
                        String defName = extractFunctionName(function.getText());
                        if (defName.equals(functionName)) {
                            definitionCache.put(functionName, function);
                            return function;
                        }
                    }
                }
            }
        }
        
        definitionCache.put(functionName, null);
        return null;
    }

    @Override
    protected boolean isFunctionDefinition(PsiElement element) {
        if (element == null) return false;
        String text = element.getText().trim();
        return text.startsWith("func ") && FUNC_PATTERN.matcher(text).find();
    }

    private boolean isValidFunctionCall(String text) {
        if (text == null || text.isEmpty()) return false;
        
        // Remove string literals to avoid false matches
        text = STRING_PATTERN.matcher(text).replaceAll("");
        
        // Check if it's a function call pattern
        java.util.regex.Matcher matcher = FUNC_CALL_PATTERN.matcher(text);
        if (!matcher.find()) return false;
        
        String functionName = matcher.group(1);
        
        // Skip builtin functions
        return !BUILTIN_PATTERN.matcher(functionName).matches();
    }
}
