package bf.com.copy2md.formatter;

import bf.com.copy2md.model.FunctionContext;

import java.util.Set;

public class MarkdownFormatter {
    public String format(Set<FunctionContext> contexts) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Function Call Context\n\n");

        for (FunctionContext context : contexts) {
            markdown.append("## ").append(context.getName()).append("\n\n");
            markdown.append("File: ").append(context.getFileName()).append("\n");
            markdown.append("Package: ").append(context.getPackageName()).append("\n\n");
            markdown.append("```java\n");
            markdown.append(context.getSourceText()).append("\n");
            markdown.append("```\n\n");
        }

        return markdown.toString();
    }
}