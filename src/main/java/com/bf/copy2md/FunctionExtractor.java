package com.bf.copy2md;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.openapi.util.text.StringUtil;

import java.util.*;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;

public class FunctionExtractor {
    private static final Logger LOG = Logger.getInstance(FunctionExtractor.class);
    private final Project project;
    private final Set<String> processedFunctions = new HashSet<>();
    private final Map<String, String> dependencies = new HashMap<>();
    private final Map<String, PsiElement> functionCache = new ConcurrentHashMap<>();
    private int maxRecursionDepth = 10;
    private boolean debug = false;

    // 标准库和内置函数模式
    private static final Pattern BUILTIN_PATTERN = Pattern.compile(
        "^(print|len|str|int|float|list|dict|set|tuple|" +
        "open|range|enumerate|zip|map|filter|sorted|reversed|" +
        "sum|min|max|abs|round|pow|divmod|isinstance|issubclass|" +
        "hasattr|getattr|setattr|delattr|callable|type)$"
    );
    
    // 装饰器模式
    private static final Pattern DECORATOR_PATTERN = Pattern.compile(
        "@(staticmethod|classmethod|property|abstractmethod|" +
        "cached_property|lru_cache|contextmanager|wraps|" +
        "pytest\\.fixture|pytest\\.mark|mock\\.patch)"
    );

    // 字符串模式
    private static final Pattern STRING_PATTERN = Pattern.compile(
        "(\"([^\"\\\\]|\\\\.)*\")|('([^'\\\\]|\\\\.)*')|" +
        "(\"\"\"([^\"\"\"\\\\]|\\\\.)*\"\"\")|" +
        "('''([^'''\\\\]|\\\\.)*''')"
    );

    // 函数定义模式
    private static final Pattern FUNCTION_DEF_PATTERN = Pattern.compile(
        "^\\s*(async\\s+)?def\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\("
    );

    public FunctionExtractor(Project project) {
        this.project = project;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public String extractFunction(PsiElement functionElement) {
        try {
            return doExtractFunction(functionElement);
        } catch (Exception e) {
            LOG.error("Error extracting function", e);
            return "Error extracting function: " + e.getMessage();
        }
    }

    private String doExtractFunction(PsiElement functionElement) {
        processedFunctions.clear();
        dependencies.clear();
        functionCache.clear();

        if (functionElement == null) {
            LOG.warn("Null function element provided");
            return "No function element found";
        }

        StringBuilder result = new StringBuilder();
        result.append("# Project: ").append(project.getName()).append("\n");
        result.append("# File: ").append(functionElement.getContainingFile().getVirtualFile().getPath()).append("\n\n");

        // 提取主函数代码
        String mainFunction = extractFunctionCode(functionElement);
        if (mainFunction.isEmpty()) {
            LOG.warn("No valid function code extracted from element");
            return "No valid function found";
        }

        String mainFunctionName = extractFunctionName(mainFunction);
        if (mainFunctionName.isEmpty()) {
            LOG.warn("Could not extract function name from: " + StringUtil.first(mainFunction, 100, true));
            return "Could not determine function name";
        }

        processedFunctions.add(mainFunctionName);
        result.append(mainFunction).append("\n");

        // 提取依赖
        try {
            extractDependencies(functionElement, 0);
        } catch (Exception e) {
            LOG.warn("Error extracting dependencies", e);
        }

        // 添加依赖函数
        for (Map.Entry<String, String> entry : dependencies.entrySet()) {
            if (!entry.getKey().equals(mainFunctionName)) {
                result.append("\n# Dependency: ").append(entry.getKey()).append("\n");
                result.append(entry.getValue()).append("\n");
            }
        }

        return result.toString();
    }

    private String extractFunctionCode(PsiElement element) {
        String text = element.getText();
        String[] lines = text.split("\n");
        StringBuilder functionCode = new StringBuilder();
        boolean inFunction = false;
        int indentLevel = -1;
        int bracketCount = 0;
        boolean inDocstring = false;
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            
            // 处理文档字符串
            if (trimmedLine.startsWith("\"\"\"") || trimmedLine.startsWith("'''")) {
                inDocstring = !inDocstring || trimmedLine.endsWith("\"\"\"") || trimmedLine.endsWith("'''");
            }
            
            // 处理装饰器和函数定义
            if (!inDocstring && (trimmedLine.startsWith("@") || 
                FUNCTION_DEF_PATTERN.matcher(trimmedLine).find())) {
                inFunction = true;
                indentLevel = line.length() - trimmedLine.length();
                functionCode.append(line).append("\n");
                bracketCount += countBrackets(trimmedLine);
                continue;
            }
            
            if (inFunction) {
                if (trimmedLine.isEmpty()) {
                    functionCode.append(line).append("\n");
                } else {
                    int currentIndent = line.length() - trimmedLine.length();
                    bracketCount += countBrackets(trimmedLine);
                    
                    if (!inDocstring && currentIndent <= indentLevel && bracketCount <= 0 && 
                        !trimmedLine.startsWith("@") && !trimmedLine.endsWith("\\")) {
                        break;
                    }
                    functionCode.append(line).append("\n");
                }
            }
        }
        
        String result = functionCode.toString().trim();
        result = result.replaceAll("\\s*->\\s*[^:]+:\\s*\\.{3}\\s*$", "");
        
        if (debug) {
            LOG.info("Extracted function code:\n" + result);
        }
        
        return result;
    }

    private void extractDependencies(PsiElement element, int depth) {
        if (depth >= maxRecursionDepth) {
            LOG.warn("Max recursion depth reached at: " + element.getText());
            return;
        }

        Collection<PsiElement> functionCalls = PsiTreeUtil.findChildrenOfType(element, PsiElement.class);
        
        for (PsiElement call : functionCalls) {
            if (isLocalFunctionCall(call)) {
                String functionName = extractFunctionName(call.getText());
                
                if (!processedFunctions.contains(functionName)) {
                    processedFunctions.add(functionName);
                    
                    PsiElement calledFunction = findFunctionDefinition(functionName);
                    if (calledFunction != null) {
                        String functionCode = extractFunctionCode(calledFunction);
                        if (!functionCode.isEmpty()) {
                            dependencies.put(functionName, functionCode);
                            if (debug) {
                                LOG.info("Found dependency: " + functionName);
                            }
                            extractDependencies(calledFunction, depth + 1);
                        }
                    } else if (debug) {
                        LOG.info("Could not find definition for: " + functionName);
                    }
                }
            }
        }
    }

    private PsiElement findFunctionDefinition(String functionName) {
        // 先检查缓存
        if (functionCache.containsKey(functionName)) {
            return functionCache.get(functionName);
        }

        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<VirtualFile> files = FilenameIndex.getAllFilesByExt(project, "py", scope);
        
        for (VirtualFile file : files) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile != null) {
                Collection<PsiElement> functions = PsiTreeUtil.findChildrenOfType(psiFile, PsiElement.class);
                for (PsiElement function : functions) {
                    if (isFunctionDefinition(function)) {
                        String defName = extractFunctionName(function.getText());
                        if (defName.equals(functionName)) {
                            functionCache.put(functionName, function);
                            return function;
                        }
                    }
                }
            }
        }
        
        functionCache.put(functionName, null);
        return null;
    }

    private String extractFunctionName(String text) {
        // 移除字符串内容，避免误判
        text = STRING_PATTERN.matcher(text).replaceAll("");
        
        // 处理方法调用
        if (text.contains(".")) {
            String[] parts = text.split("\\.");
            if (parts.length >= 2) {
                String prefix = parts[0].trim();
                // 处理self和super调用
                if (prefix.equals("self") || prefix.equals("super()")) {
                    String methodPart = parts[1];
                    int bracketIndex = methodPart.indexOf('(');
                    return bracketIndex > 0 ? methodPart.substring(0, bracketIndex).trim() : methodPart.trim();
                }
            }
            // 获取链式调用的最后一个函数名
            String lastPart = parts[parts.length - 1];
            int bracketIndex = lastPart.indexOf('(');
            return bracketIndex > 0 ? lastPart.substring(0, bracketIndex).trim() : lastPart.trim();
        }
        
        // 处理普通函数调用
        int bracketIndex = text.indexOf('(');
        if (bracketIndex > 0) {
            String name = text.substring(0, bracketIndex).trim();
            // 如果是函数定义，提取def后面的名称
            if (name.startsWith("def ")) {
                name = name.substring(4);
            } else if (name.startsWith("async def ")) {
                name = name.substring(10);
            }
            return name.trim();
        }
        
        return text.trim();
    }

    private boolean isLocalFunctionCall(PsiElement element) {
        String text = element.getText();
        
        // 排除字符串中的函数调用
        if (STRING_PATTERN.matcher(text).find()) {
            return false;
        }
        
        // 基本函数调用模式
        if (!text.matches("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*\\s*\\(.*\\)")) {
            return false;
        }

        String functionName = extractFunctionName(text);
        return !BUILTIN_PATTERN.matcher(functionName).matches();
    }

    private boolean isFunctionDefinition(PsiElement element) {
        String text = element.getText().trim();
        
        // 处理装饰器
        String[] lines = text.split("\n");
        boolean hasDecorator = false;
        
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("@")) {
                if (!DECORATOR_PATTERN.matcher(line).matches()) {
                    hasDecorator = true;
                }
            } else if (line.startsWith("def ") || line.startsWith("async def ")) {
                return true;
            }
        }
        
        return hasDecorator;
    }

    private int countBrackets(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == '(' || c == '[' || c == '{') count++;
            if (c == ')' || c == ']' || c == '}') count--;
        }
        return count;
    }
}
