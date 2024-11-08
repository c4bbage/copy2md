package bf.com.copy2md.service;

import bf.com.copy2md.model.ExtractionConfig;
import bf.com.copy2md.model.FunctionContext;
import com.intellij.openapi.components.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public final class CodeExtractionService {
    public String extractCode(FunctionContext context, ExtractionConfig config) {
        StringBuilder result = new StringBuilder();
        extractRecursively(context, config, new HashSet<>(), result);
        return result.toString();
    }

    private void extractRecursively(FunctionContext context, ExtractionConfig config,
                                    Set<String> visited, StringBuilder result) {
        if (!visited.add(context.getSignature())) {
            return;
        }

        // Add file header
        result.append("## ").append(context.getFileName())
                .append(" - ").append(context.getName())
                .append("\n\n");

        // Add source code
        result.append("```java\n")
                .append(context.getSourceText())
                .append("\n```\n\n");

        // Process dependencies
        for (FunctionContext dependency : context.getDependencies()) {
            extractRecursively(dependency, config, visited, result);
        }
    }
}