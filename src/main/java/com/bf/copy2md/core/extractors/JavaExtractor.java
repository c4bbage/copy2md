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

// 添加必要的PSI类导入
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiSuperExpression;
import com.intellij.psi.PsiMethodCallExpression;

public class JavaExtractor extends BaseExtractor {
    private static final Pattern METHOD_PATTERN = Pattern.compile(
        "^\\s*(public|private|protected|static|\\s)*\\s*[\\w<>\\[\\]]+\\s+\\w+\\s*\\("
    );

    private static final Pattern ANNOTATION_PATTERN = Pattern.compile(
        "@(Override|Deprecated|SuppressWarnings|FunctionalInterface|" +
        "Test|Before|After|BeforeClass|AfterClass|Ignore|RunWith|" +
        "Autowired|Component|Service|Repository|Controller|RestController|" +
        "RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping)"
    );

    public JavaExtractor(Project project) {
        super(project);
    }

    @Override
    public boolean canHandle(String fileExtension) {
        return fileExtension != null && fileExtension.equals("java");
    }

    @Override
    public String extractFunction(PsiElement element) {
        try {
            clearState();
            if (element == null) {
                LOG.warn("Null method element provided");
                return "No method element found";
            }

            StringBuilder result = new StringBuilder();
            result.append("// Project: ").append(project.getName()).append("\n");
            result.append("// File: ").append(element.getContainingFile().getVirtualFile().getPath()).append("\n\n");

            String mainMethod = extractFunctionCode(element);
            if (mainMethod.isEmpty()) {
                LOG.warn("No valid method code extracted from element");
                return "No valid method found";
            }

            String mainMethodName = extractFunctionName(mainMethod);
            result.append(mainMethod).append("\n");

            extractDependencies(element, 0);

            // Add dependencies
            for (Map.Entry<String, String> entry : dependencies.entrySet()) {
                if (!entry.getKey().equals(mainMethodName)) {
                    result.append("\n// Dependency: ").append(entry.getKey()).append("\n");
                    result.append(entry.getValue()).append("\n");
                }
            }

            return result.toString();
        } catch (Exception e) {
            LOG.error("Error extracting method", e);
            return "Error extracting method: " + e.getMessage();
        }
    }

    @Override
    protected String extractFunctionCode(PsiElement element) {
        if (!(element instanceof PsiMethod)) {
            return "";
        }

        PsiMethod method = (PsiMethod) element;
        
        // 检查是否是接口方法
        if (method.getContainingClass() != null && method.getContainingClass().isInterface()) {
            throw new ExtractorException(ExtractorException.ErrorType.INTERFACE_METHOD,
                "Method '" + method.getName() + "' is part of interface " + method.getContainingClass().getName());
        }

        // 检查抽象方法
        if (method.getModifierList().hasModifierProperty(PsiModifier.ABSTRACT)) {
            throw new ExtractorException(ExtractorException.ErrorType.ABSTRACT_METHOD,
                "Method '" + method.getName() + "' is abstract");
        }

        // 检查泛型方法
        if (method.getTypeParameters().length > 0) {
            // 获取完整的泛型上下文
            PsiTypeParameter[] typeParams = method.getTypeParameters();
            StringBuilder typeContext = new StringBuilder();
            for (PsiTypeParameter param : typeParams) {
                if (typeContext.length() > 0) typeContext.append(", ");
                typeContext.append(param.getName());
                PsiClassType[] bounds = param.getExtendsListTypes();
                if (bounds.length > 0) {
                    typeContext.append(" extends ").append(bounds[0].getPresentableText());
                }
            }
            log("Processing generic method with type parameters: " + typeContext);
        }

        StringBuilder code = new StringBuilder();

        // 处理注解
        PsiAnnotation[] annotations = method.getAnnotations();
        for (PsiAnnotation annotation : annotations) {
            code.append(annotation.getText()).append("\n");
        }

        // 添加方法签名和主体
        code.append(method.getText());

        // 检查super调用
        if (containsSuperCall(method)) {
            PsiMethod superMethod = findSuperMethod(method);
            if (superMethod != null) {
                String superClassName = superMethod.getContainingClass() != null ? 
                    superMethod.getContainingClass().getName() : "unknown";
                log("Found super method in class: " + superClassName);
                dependencies.put(superMethod.getName() + "_super", extractFunctionCode(superMethod));
            } else {
                throw new ExtractorException(ExtractorException.ErrorType.SUPER_METHOD_NOT_FOUND,
                    "Super method for '" + method.getName() + "' not found");
            }
        }
        
        if (debug) {
            LOG.info("Extracted method code:\n" + code.toString());
        }

        return code.toString();
    }

    private boolean containsSuperCall(PsiMethod method) {
        return PsiTreeUtil.findChildrenOfType(method, PsiSuperExpression.class).size() > 0;
    }

    private PsiMethod findSuperMethod(PsiMethod method) {
        if (method.getContainingClass() == null) return null;
        PsiClass superClass = method.getContainingClass().getSuperClass();
        if (superClass == null) return null;

        for (PsiMethod superMethod : superClass.findMethodsByName(method.getName(), true)) {
            if (methodSignaturesMatch(method, superMethod)) {
                return superMethod;
            }
        }
        return null;
    }

    private boolean methodSignaturesMatch(PsiMethod method1, PsiMethod method2) {
        if (!method1.getName().equals(method2.getName())) return false;
        
        PsiParameter[] params1 = method1.getParameterList().getParameters();
        PsiParameter[] params2 = method2.getParameterList().getParameters();
        
        if (params1.length != params2.length) return false;
        
        for (int i = 0; i < params1.length; i++) {
            if (!params1[i].getType().equals(params2[i].getType())) {
                return false;
            }
        }
        
        return true;
    }

    @Override
    protected void extractDependencies(PsiElement element, int depth) {
        if (depth >= maxRecursionDepth) {
            log("Max recursion depth reached at: " + element.getText());
            return;
        }

        Collection<PsiMethodCallExpression> methodCalls = 
            PsiTreeUtil.findChildrenOfType(element, PsiMethodCallExpression.class);

        for (PsiMethodCallExpression call : methodCalls) {
            PsiMethod calledMethod = call.resolveMethod();
            if (calledMethod != null && isLocalMethod(calledMethod)) {
                String methodName = calledMethod.getName();
                if (!dependencies.containsKey(methodName)) {
                    String methodCode = extractFunctionCode(calledMethod);
                    if (!methodCode.isEmpty()) {
                        dependencies.put(methodName, methodCode);
                        log("Found dependency: " + methodName);
                        extractDependencies(calledMethod, depth + 1);
                    }
                }
            }
        }
    }

    @Override
    protected PsiElement findFunctionDefinition(String functionName) {
        if (definitionCache.containsKey(functionName)) {
            return definitionCache.get(functionName);
        }

        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<VirtualFile> files = FilenameIndex.getAllFilesByExt(project, "java", scope);
        
        for (VirtualFile file : files) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile instanceof PsiJavaFile) {
                PsiJavaFile javaFile = (PsiJavaFile) psiFile;
                for (PsiClass psiClass : javaFile.getClasses()) {
                    for (PsiMethod method : psiClass.getMethods()) {
                        if (method.getName().equals(functionName)) {
                            definitionCache.put(functionName, method);
                            return method;
                        }
                    }
                }
            }
        }
        
        definitionCache.put(functionName, null);
        return null;
    }

    @Override
    protected String extractFunctionName(String text) {
        if (text == null || text.isEmpty()) return "";

        // Find method name from signature
        java.util.regex.Matcher matcher = METHOD_PATTERN.matcher(text);
        if (matcher.find()) {
            String signature = matcher.group();
            String[] parts = signature.trim().split("\\s+");
            if (parts.length >= 2) {
                String methodName = parts[parts.length - 1];
                int parenIndex = methodName.indexOf('(');
                if (parenIndex != -1) {
                    return methodName.substring(0, parenIndex);
                }
                return methodName;
            }
        }
        return "";
    }

    @Override
    protected boolean isFunctionDefinition(PsiElement element) {
        return element instanceof PsiMethod;
    }

    private boolean isLocalMethod(PsiMethod method) {
        String qualifiedName = "";
        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
            qualifiedName = containingClass.getQualifiedName();
        }
        return qualifiedName != null && 
               !qualifiedName.startsWith("java.") && 
               !qualifiedName.startsWith("javax.") &&
               !qualifiedName.startsWith("com.sun.") &&
               !qualifiedName.startsWith("sun.");
    }
}
