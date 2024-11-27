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

public class PythonExtractor extends BaseExtractor {
    private static final Pattern BUILTIN_PATTERN = Pattern.compile(
        "^(print|len|str|int|float|list|dict|set|tuple|" +
        "open|range|enumerate|zip|map|filter|sorted|reversed|" +
        "sum|min|max|abs|round|pow|divmod|isinstance|issubclass|" +
        "hasattr|getattr|setattr|delattr|callable|type)$"
    );

    private static final Pattern DECORATOR_PATTERN = Pattern.compile(
        "@(staticmethod|classmethod|property|abstractmethod|" +
        "cached_property|lru_cache|contextmanager|wraps|" +
        "pytest\\.fixture|pytest\\.mark|mock\\.patch)"
    );

    private static final Pattern STRING_PATTERN = Pattern.compile(
        "(\"([^\"\\\\]|\\\\.)*\")|('([^'\\\\]|\\\\.)*')|" +
        "(\"\"\"([^\"\"\"\\\\]|\\\\.)*\"\"\")|" +
        "('''([^'''\\\\]|\\\\.)*''')"
    );

    private static final Pattern FUNCTION_DEF_PATTERN = Pattern.compile(
        "^\\s*(async\\s+)?def\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\("
    );

    public PythonExtractor(Project project) {
        super(project);
    }

    @Override
    public boolean canHandle(String fileExtension) {
        return fileExtension != null && 
               (fileExtension.equals("py") || fileExtension.equals("pyw"));
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
            result.append("# Project: ").append(project.getName()).append("\n");
            result.append("# File: ").append(element.getContainingFile().getVirtualFile().getPath()).append("\n\n");

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
                    result.append("\n# Dependency: ").append(entry.getKey()).append("\n");
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
        String text = element.getText();
        String[] lines = text.split("\n");
        StringBuilder functionCode = new StringBuilder();
        boolean inFunction = false;
        int indentLevel = -1;
        int bracketCount = 0;
        boolean inDocstring = false;
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            
            if (trimmedLine.startsWith("\"\"\"") || trimmedLine.startsWith("'''")) {
                inDocstring = !inDocstring || trimmedLine.endsWith("\"\"\"") || trimmedLine.endsWith("'''");
            }
            
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

    @Override
    protected void extractDependencies(PsiElement element, int depth) {
        if (depth >= maxRecursionDepth) {
            log("Max recursion depth reached at: " + element.getText());
            return;
        }

        Collection<PsiElement> functionCalls = PsiTreeUtil.findChildrenOfType(element, PsiElement.class);
        for (PsiElement call : functionCalls) {
            String text = call.getText();
            if (isValidFunctionCall(text)) {
                String functionName = extractFunctionName(text);
                if (!dependencies.containsKey(functionName)) {
                    PsiElement calledFunction = findFunctionDefinition(functionName);
                    if (calledFunction != null) {
                        String functionCode = extractFunctionCode(calledFunction);
                        if (!functionCode.isEmpty()) {
                            dependencies.put(functionName, functionCode);
                            log("Found dependency: " + functionName);
                            extractDependencies(calledFunction, depth + 1);
                        }
                    } else {
                        log("Could not find definition for: " + functionName);
                    }
                }
            }
        }
    }

    @Override
    protected PsiElement findFunctionDefinition(String functionName) {
        if (isDefinitionCached(functionName)) {
            return getCachedDefinition(functionName);
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
                            cacheDefinition(functionName, function);
                            return function;
                        }
                    }
                }
            }
        }
        
        cacheDefinition(functionName, null);
        return null;
    }

    @Override
    protected String extractFunctionName(String text) {
        text = STRING_PATTERN.matcher(text).replaceAll("");
        
        if (text.contains("def ")) {
            int defIndex = text.indexOf("def ");
            int nameStart = defIndex + 4;
            int nameEnd = text.indexOf('(', nameStart);
            if (nameEnd != -1) {
                return text.substring(nameStart, nameEnd).trim();
            }
        }
        
        return "";
    }

    @Override
    protected boolean isFunctionDefinition(PsiElement element) {
        if (element == null) return false;
        String text = element.getText().trim();
        
        // Handle decorators
        String[] lines = text.split("\n");
        boolean hasDecorator = false;
        
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("@")) {
                if (DECORATOR_PATTERN.matcher(line).find()) {
                    hasDecorator = true;
                }
            } else if (FUNCTION_DEF_PATTERN.matcher(line).find()) {
                return true;
            }
        }
        
        return hasDecorator;
    }

    private boolean isValidFunctionCall(String text) {
        if (text == null || text.isEmpty()) return false;
        
        // Remove string content
        text = STRING_PATTERN.matcher(text).replaceAll("");
        
        // Check if it's a function call
        if (!text.contains("(")) return false;
        
        String functionName = extractFunctionName(text);
        return !BUILTIN_PATTERN.matcher(functionName).matches();
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
