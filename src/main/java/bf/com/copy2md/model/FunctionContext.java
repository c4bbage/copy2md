package bf.com.copy2md.model;

import com.intellij.psi.PsiElement;
import java.util.HashSet;
import java.util.Set;

public class FunctionContext {
    private final PsiElement function;
    private final String name;
    private final String fileName;
    private final String sourceText;
    private final String packageName;
    private final Set<FunctionContext> dependencies;
    private final boolean isProjectFunction;

    public FunctionContext(PsiElement function, String name,
                           String fileName, String sourceText,
                           String packageName, boolean isProjectFunction) {
        this.function = function;
        this.name = name;
        this.fileName = fileName;
        this.sourceText = sourceText;
        this.packageName = packageName;
        this.isProjectFunction = isProjectFunction;
        this.dependencies = new HashSet<>();
    }

    // 保留原有的getter和方法
    // 添加新的getter
    public String getPackageName() { return packageName; }
    public boolean isProjectFunction() { return isProjectFunction; }
    public String getSignature() {
        return packageName + "." + name;
    }
    public String getName() { return name; }
    public String getFileName() { return fileName; }
    public String getSourceText() { return sourceText; }
    public Set<FunctionContext> getDependencies() { return dependencies; }

    // 添加 addDependency 方法
    public void addDependency(FunctionContext dependency) {
        dependencies.add(dependency);
    }
}