package com.bf.copy2md.core.extractors;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import java.util.*;

public abstract class BaseExtractor implements LanguageExtractor {
    protected static final Logger LOG = Logger.getInstance(BaseExtractor.class);
    protected final Project project;
    protected final Map<String, String> dependencies = new HashMap<>();
    protected final Map<String, PsiElement> definitionCache = new HashMap<>();
    protected boolean debug = false;
    protected int maxRecursionDepth = 10;  // 默认最大递归深度

    public BaseExtractor(Project project) {
        this.project = project;
    }

    @Override
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    @Override
    public Map<String, String> getDependencies() {
        return new HashMap<>(dependencies);
    }

    /**
     * 设置最大递归深度
     */
    public void setMaxRecursionDepth(int depth) {
        this.maxRecursionDepth = depth;
    }

    /**
     * 获取当前最大递归深度
     */
    public int getMaxRecursionDepth() {
        return maxRecursionDepth;
    }

    protected void log(String message) {
        if (debug) {
            LOG.info(message);
        }
    }

    protected void clearState() {
        dependencies.clear();
        definitionCache.clear();
    }

    /**
     * 从缓存中获取函数定义
     */
    protected PsiElement getCachedDefinition(String functionName) {
        return definitionCache.get(functionName);
    }

    /**
     * 将函数定义添加到缓存中
     */
    protected void cacheDefinition(String functionName, PsiElement definition) {
        if (definition != null) {
            definitionCache.put(functionName, definition);
        }
    }

    /**
     * 检查函数定义是否在缓存中
     */
    protected boolean isDefinitionCached(String functionName) {
        return definitionCache.containsKey(functionName);
    }

    protected abstract String extractFunctionCode(PsiElement element);
    protected abstract String extractFunctionName(String text);
    protected abstract boolean isFunctionDefinition(PsiElement element);
    protected abstract void extractDependencies(PsiElement element, int depth);
    protected abstract PsiElement findFunctionDefinition(String functionName);
}
