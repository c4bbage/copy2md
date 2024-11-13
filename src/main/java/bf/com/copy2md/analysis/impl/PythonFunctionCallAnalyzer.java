//package bf.com.copy2md.analysis.impl;
//import com.intellij.psi.*;
//import com.intellij.psi.util.PsiTreeUtil;
//import com.intellij.openapi.diagnostic.Logger;
//import org.jetbrains.annotations.NotNull;
//import org.jetbrains.annotations.Nullable;
//import com.jetbrains.python.psi.*;
//
//import java.util.*;
//
///**
// * Python函数分析器
// * 用于分析Python代码中的函数定义及其上下文
// */
//public class PythonFunctionCallAnalyzer {
//    private static final Logger LOG = Logger.getInstance(PythonFunctionCallAnalyzer.class);
//
//    /**
//     * 函数信息数据类
//     */
//    public static class FunctionInfo {
//        private final String name;
//        private final int startLine;
//        private final int endLine;
//        private final String code;
//        private final List<String> imports;
//        private final String docString;
//        private final List<String> decorators;
//        private final String className;
//        private final String returnType;
//        private final List<String> parameters;
//
//        public FunctionInfo(String name, int startLine, int endLine, String code,
//                            List<String> imports, String docString, List<String> decorators,
//                            String className, String returnType, List<String> parameters) {
//            this.name = name;
//            this.startLine = startLine;
//            this.endLine = endLine;
//            this.code = code;
//            this.imports = imports;
//            this.docString = docString;
//            this.decorators = decorators;
//            this.className = className;
//            this.returnType = returnType;
//            this.parameters = parameters;
//        }
//
//        // Getters
//        public String getName() { return name; }
//        public int getStartLine() { return startLine; }
//        public int getEndLine() { return endLine; }
//        public String getCode() { return code; }
//        public List<String> getImports() { return imports; }
//        public String getDocString() { return docString; }
//        public List<String> getDecorators() { return decorators; }
//        public String getClassName() { return className; }
//        public String getReturnType() { return returnType; }
//        public List<String> getParameters() { return parameters; }
//    }
//
//    private final PsiFile psiFile;
//    private final List<FunctionInfo> functionInfoList;
//    private final Set<String> importedNames;
//
//    public PythonFunctionCallAnalyzer(@NotNull PsiFile psiFile) {
//        this.psiFile = psiFile;
//        this.functionInfoList = new ArrayList<>();
//        this.importedNames = new HashSet<>();
//    }
//
//    /**
//     * 分析文件中的函数定义
//     * @return 函数信息列表
//     */
//    public List<FunctionInfo> analyze() {
//        try {
//            collectImports();
//            collectFunctions();
//            return functionInfoList;
//        } catch (Exception e) {
//            LOG.error("Error analyzing Python file: " + psiFile.getName(), e);
//            return Collections.emptyList();
//        }
//    }
//
//    private void collectImports() {
//        PsiTreeUtil.processElements(psiFile, element -> {
//            if (element instanceof PsiImportStatement || element instanceof PsiFromImportStatement) {
//                processImportElement(element);
//            }
//            return true;
//        });
//    }
//
//    private void processImportElement(PsiElement element) {
//        // 处理import语句
//        if (element instanceof PsiImportStatement) {
//            PsiImportStatement importStmt = (PsiImportStatement) element;
//            importedNames.add(importStmt.getImportedQName().toString());
//        }
//        // 处理from import语句
//        else if (element instanceof PsiFromImportStatement) {
//            PsiFromImportStatement fromImport = (PsiFromImportStatement) element;
//            for (String name : fromImport.getImportedNames()) {
//                importedNames.add(name);
//            }
//        }
//    }
//
//    private void collectFunctions() {
//        PsiTreeUtil.processElements(psiFile, element -> {
//            if (element instanceof PsiFunctionDefinition) {
//                processFunctionDefinition((PsiFunctionDefinition) element);
//            }
//            return true;
//        });
//    }
//
//    private void processFunctionDefinition(PsiFunctionDefinition function) {
//        String name = function.getName();
//        int startLine = function.getTextRange().getStartOffset();
//        int endLine = function.getTextRange().getEndOffset();
//        String code = function.getText();
//
//        // 收集函数相关的上下文信息
//        List<String> imports = getRelevantImports(function);
//        String docString = extractDocString(function);
//        List<String> decorators = extractDecorators(function);
//        String className = extractClassName(function);
//        String returnType = extractReturnType(function);
//        List<String> parameters = extractParameters(function);
//
//        FunctionInfo functionInfo = new FunctionInfo(
//                name, startLine, endLine, code, imports,
//                docString, decorators, className, returnType, parameters
//        );
//        functionInfoList.add(functionInfo);
//    }
//
//    @NotNull
//    private List<String> getRelevantImports(PsiFunctionDefinition function) {
//        // 分析函数中使用的导入
//        List<String> relevantImports = new ArrayList<>();
//        String functionText = function.getText();
//        for (String importedName : importedNames) {
//            if (functionText.contains(importedName)) {
//                relevantImports.add(importedName);
//            }
//        }
//        return relevantImports;
//    }
//
//    @Nullable
//    private String extractDocString(PsiFunctionDefinition function) {
//        // 提取函数的文档字符串
//        PsiElement firstChild = function.getFirstChild();
//        if (firstChild instanceof PsiDocString) {
//            return firstChild.getText();
//        }
//        return null;
//    }
//
//    @NotNull
//    private List<String> extractDecorators(PsiFunctionDefinition function) {
//        // 提取装饰器
//        List<String> decorators = new ArrayList<>();
//        PsiElement[] decoratorElements = function.getDecorators();
//        if (decoratorElements != null) {
//            for (PsiElement decorator : decoratorElements) {
//                decorators.add(decorator.getText());
//            }
//        }
//        return decorators;
//    }
//
//    @Nullable
//    private String extractClassName(PsiFunctionDefinition function) {
//        // 获取所属类名
//        PsiElement parent = function.getParent();
//        if (parent instanceof PsiClassDefinition) {
//            return ((PsiClassDefinition) parent).getName();
//        }
//        return null;
//    }
//
//    @Nullable
//    private String extractReturnType(PsiFunctionDefinition function) {
//        // 提取返回类型注解
//        PsiAnnotation returnAnnotation = function.getReturnTypeAnnotation();
//        return returnAnnotation != null ? returnAnnotation.getText() : null;
//    }
//
//    @NotNull
//    private List<String> extractParameters(PsiFunctionDefinition function) {
//        // 提取参数列表
//        List<String> parameters = new ArrayList<>();
//        PsiParameter[] params = function.getParameters();
//        for (PsiParameter param : params) {
//            parameters.add(param.getText());
//        }
//        return parameters;
//    }
//
//    /**
//     * 将函数信息转换为Markdown格式
//     */
//    public String convertToMarkdown(FunctionInfo functionInfo) {
//        StringBuilder markdown = new StringBuilder();
//
//        // 添加标题
//        markdown.append("## Function: ").append(functionInfo.getName()).append("\n\n");
//
//        // 添加类信息(如果有)
//        if (functionInfo.getClassName() != null) {
//            markdown.append("Class: `").append(functionInfo.getClassName()).append("`\n\n");
//        }
//
//        // 添加函数签名
//        markdown.append("### Signature\n\n```python\n");
//        if (!functionInfo.getDecorators().isEmpty()) {
//            for (String decorator : functionInfo.getDecorators()) {
//                markdown.append(decorator).append("\n");
//            }
//        }
//        markdown.append("def ").append(functionInfo.getName()).append("(");
//        markdown.append(String.join(", ", functionInfo.getParameters()));
//        if (functionInfo.getReturnType() != null) {
//            markdown.append(") -> ").append(functionInfo.getReturnType());
//        } else {
//            markdown.append(")");
//        }
//        markdown.append("\n```\n\n");
//
//        // 添加文档字符串
//        if (functionInfo.getDocString() != null) {
//            markdown.append("### Documentation\n\n```python\n")
//                    .append(functionInfo.getDocString())
//                    .append("\n```\n\n");
//        }
//
//        // 添加相关导入
//        if (!functionInfo.getImports().isEmpty()) {
//            markdown.append("### Imports\n\n```python\n");
//            for (String import_ : functionInfo.getImports()) {
//                markdown.append(import_).append("\n");
//            }
//            markdown.append("```\n\n");
//        }
//
//        // 添加完整代码
//        markdown.append("### Full Code\n\n```python\n")
//                .append(functionInfo.getCode())
//                .append("\n```\n");
//
//        return markdown.toString();
//    }
//}