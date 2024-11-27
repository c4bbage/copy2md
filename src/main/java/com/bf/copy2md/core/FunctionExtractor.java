package com.bf.copy2md.core;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.bf.copy2md.core.extractors.ExtractorFactory;
import com.bf.copy2md.core.extractors.LanguageExtractor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FunctionExtractor {
    private static final Logger LOG = Logger.getInstance(FunctionExtractor.class);
    private final Project project;
    private final Set<String> processedFunctions = new HashSet<>();
    private final Map<String, String> dependencies = new HashMap<>();
    private final Map<String, PsiElement> functionCache = new ConcurrentHashMap<>();
    private boolean debug = false;

    public FunctionExtractor(Project project) {
        this.project = project;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public String extractFunctionWithDependencies(PsiElement element) {
        if (element == null) {
            LOG.warn("Null element provided to extractFunctionWithDependencies");
            return "";
        }

        try {
            // 清除之前的状态
            processedFunctions.clear();
            dependencies.clear();
            functionCache.clear();

            // 获取文件扩展名
            VirtualFile virtualFile = element.getContainingFile().getVirtualFile();
            String fileExtension = virtualFile != null ? virtualFile.getExtension() : "";

            // 获取语言特定的提取器
            LanguageExtractor extractor = ExtractorFactory.getExtractor(fileExtension, project);
            if (extractor == null) {
                LOG.warn("No extractor found for file extension: " + fileExtension);
                return "";
            }

            extractor.setDebug(debug);
            return extractor.extractFunction(element);

        } catch (Exception e) {
            LOG.error("Error extracting function", e);
            return "Error extracting function: " + e.getMessage();
        }
    }

    public String extractFunctionWithDependencies(PsiFile file, int offset) {
        if (file == null) {
            LOG.warn("Null file provided to extractFunctionWithDependencies");
            return "";
        }

        try {
            PsiElement element = file.findElementAt(offset);
            if (element == null) {
                LOG.warn("No element found at offset: " + offset);
                return "";
            }

            // 查找包含当前位置的函数定义
            while (element != null && !isFunctionDefinition(element)) {
                element = element.getParent();
            }

            if (element == null) {
                LOG.warn("No function definition found at offset: " + offset);
                return "";
            }

            return extractFunctionWithDependencies(element);

        } catch (Exception e) {
            LOG.error("Error extracting function at offset", e);
            return "Error extracting function: " + e.getMessage();
        }
    }

    private boolean isFunctionDefinition(PsiElement element) {
        String text = element.getText().trim();
        return text.startsWith("func ") || text.startsWith("def ") || text.startsWith("public ") || 
               text.startsWith("private ") || text.startsWith("protected ");
    }
}
