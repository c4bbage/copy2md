package com.bf.copy2md.core.extractors;

import com.intellij.psi.PsiElement;
import java.util.Map;

public interface LanguageExtractor {
    /**
     * Extract the function/method code and its dependencies
     */
    String extractFunction(PsiElement element);

    /**
     * Check if this extractor can handle the given file type
     */
    boolean canHandle(String fileExtension);

    /**
     * Enable or disable debug mode
     */
    void setDebug(boolean debug);

    /**
     * Set maximum recursion depth for dependency extraction
     */
    void setMaxRecursionDepth(int depth);

    /**
     * Get current maximum recursion depth
     */
    int getMaxRecursionDepth();

    /**
     * Get the extracted dependencies
     */
    Map<String, String> getDependencies();
}
