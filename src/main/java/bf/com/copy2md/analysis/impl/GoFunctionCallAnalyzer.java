//package bf.com.copy2md.analysis.impl;
//
//import com.goide.psi.*;
//import com.intellij.psi.*;
//import com.intellij.psi.util.PsiTreeUtil;
//import com.intellij.openapi.diagnostic.Logger;
//import org.jetbrains.annotations.NotNull;
//import org.jetbrains.annotations.Nullable;
//
//import java.util.*;
//
///**
// * Go函数分析器
// * 用于分析Go代码中的函数定义及其上下文
// */
//public class GoFunctionCallAnalyzer {
//    private static final Logger LOG = Logger.getInstance(GoFunctionCallAnalyzer.class);
//
//    /**
//     * Go函数信息数据类
//     */
//    public static class GoFunctionInfo {
//        private final String name;
//        private final int startLine;
//        private final int endLine;
//        private final String code;
//        private final List<String> imports;
//        private final String comment;
//        private final String receiver;
//        private final String returnType;
//        private final List<String> parameters;
//        private final String packageName;
//
//        public GoFunctionInfo(String name, int startLine, int endLine, String code,
//                              List<String> imports, String comment, String receiver,
//                              String returnType, List<String> parameters, String packageName) {
//            this.name = name;
//            this.startLine = startLine;
//            this.endLine = endLine;
//            this.code = code;
//            this.imports = imports;
//            this.comment = comment;
//            this.receiver = receiver;
//            this.returnType = returnType;
//            this.parameters = parameters;
//            this.packageName = packageName;
//        }
//
//        // Getters
//        public String getName() { return name; }
//        public int getStartLine() { return startLine; }
//        public int getEndLine() { return endLine; }
//        public String getCode() { return code; }
//        public List<String> getImports() { return imports; }
//        public String getComment() { return comment; }
//        public String getReceiver() { return receiver; }
//        public String getReturnType() { return returnType; }
//        public List<String> getParameters() { return parameters; }
//        public String getPackageName() { return packageName; }
//    }
//
//    private final GoFile goFile;
//    private final List<GoFunctionInfo> functionInfoList;
//    private final Map<String, String> importAliases;
//
//    public GoFunctionCallAnalyzer(@NotNull GoFile goFile) {
//        this.goFile = goFile;
//        this.functionInfoList = new ArrayList<>();
//        this.importAliases = new HashMap<>();
//    }
//
//    /**
//     * 分析文件中的函数定义
//     * @return 函数信息列表
//     */
//    public List<GoFunctionInfo> analyze() {
//        try {
//            collectImports();
//            collectFunctions();
//            return functionInfoList;
//        } catch (Exception e) {
//            LOG.error("Error analyzing Go file: " + goFile.getName(), e);
//            return Collections.emptyList();
//        }
//    }
//
//    private void collectImports() {
//        GoImportList importList = goFile.getImportList();
//        if (importList != null) {
//            for (GoImportSpec importSpec : importList.getImportSpecList()) {
//                String importPath = importSpec.getPath();
//                String alias = importSpec.getAlias();
//                if (alias != null) {
//                    importAliases.put(alias, importPath);
//                } else {
//                    // 使用最后一个路径组件作为默认别名
//                    String defaultAlias = importPath.substring(importPath.lastIndexOf('/') + 1);
//                    importAliases.put(defaultAlias, importPath);
//                }
//            }
//        }
//    }
//
//    private void collectFunctions() {
//        Collection<GoFunctionDeclaration> functions = PsiTreeUtil.findChildrenOfType(goFile, GoFunctionDeclaration.class);
//        for (GoFunctionDeclaration function : functions) {
//            processFunctionDeclaration(function);
//        }
//    }
//
//    private void processFunctionDeclaration(GoFunctionDeclaration function) {
//        String name = function.getName();
//        int startLine = function.getTextRange().getStartOffset();
//        int endLine = function.getTextRange().getEndOffset();
//        String code = function.getText();
//
//        // 收集函数相关的上下文信息
//        List<String> imports = getRelevantImports(function);
//        String comment = extractComment(function);
//        String receiver = extractReceiver(function);
//        String returnType = extractReturnType(function);
//        List<String> parameters = extractParameters(function);
//        String packageName = goFile.getPackageName();
//
//        GoFunctionInfo functionInfo = new GoFunctionInfo(
//                name, startLine, endLine, code, imports,
//                comment, receiver, returnType, parameters, packageName
//        );
//        functionInfoList.add(functionInfo);
//    }
//
//    @NotNull
//    private List<String> getRelevantImports(GoFunctionDeclaration function) {
//        List<String> relevantImports = new ArrayList<>();
//        String functionText = function.getText();
//
//        for (Map.Entry<String, String> entry : importAliases.entrySet()) {
//            if (functionText.contains(entry.getKey() + ".")) {
//                relevantImports.add(String.format("import %s \"%s\"",
//                        entry.getKey(), entry.getValue()));
//            }
//        }
//        return relevantImports;
//    }
//
//    @Nullable
//    private String extractComment(GoFunctionDeclaration function) {
//        PsiComment[] comments = PsiTreeUtil.getChildrenOfType(function, PsiComment.class);
//        if (comments != null && comments.length > 0) {
//            return comments[0].getText();
//        }
//        return null;
//    }
//
//    @Nullable
//    private String extractReceiver(GoFunctionDeclaration function) {
//        GoReceiver receiver = function.getReceiver();
//        return receiver != null ? receiver.getText() : null;
//    }
//
//    @Nullable
//    private String extractReturnType(GoFunctionDeclaration function) {
//        GoSignature signature = function.getSignature();
//        if (signature != null) {
//            GoResult result = signature.getResult();
//            return result != null ? result.getText() : null;
//        }
//        return null;
//    }
//
//    @NotNull
//    private List<String> extractParameters(GoFunctionDeclaration function) {
//        List<String> parameters = new ArrayList<>();
//        GoSignature signature = function.getSignature();
//        if (signature != null) {
//            GoParameters params = signature.getParameters();
//            if (params != null) {
//                for (GoParameterDeclaration param : params.getParameterDeclarationList()) {
//                    parameters.add(param.getText());
//                }
//            }
//        }
//        return parameters;
//    }
//
//    /**
//     * 将函数信息转换为Markdown格式
//     */
//    public String convertToMarkdown(GoFunctionInfo functionInfo) {
//        StringBuilder markdown = new StringBuilder();
//
//        // 添加标题
//        markdown.append("## Function: ").append(functionInfo.getName()).append("\n\n");
//
//        // 添加包信息
//        markdown.append("Package: `").append(functionInfo.getPackageName()).append("`\n\n");
//
//        // 添加函数签名
//        markdown.append("### Signature\n\n```go\n");
//        if (functionInfo.getReceiver() != null) {
//            markdown.append("func ").append(functionInfo.getReceiver()).append(" ");
//        } else {
//            markdown.append("func ");
//        }
//        markdown.append(functionInfo.getName()).append("(");
//        markdown.append(String.join(", ", functionInfo.getParameters()));
//        if (functionInfo.getReturnType() != null) {
//            markdown.append(") ").append(functionInfo.getReturnType());
//        } else {
//            markdown.append(")");
//        }
//        markdown.append("\n```\n\n");
//
//        // 添加注释
//        if (functionInfo.getComment() != null) {
//            markdown.append("### Documentation\n\n```go\n")
//                    .append(functionInfo.getComment())
//                    .append("\n```\n\n");
//        }
//
//        // 添加相关导入
//        if (!functionInfo.getImports().isEmpty()) {
//            markdown.append("### Imports\n\n```go\n");
//            for (String import_ : functionInfo.getImports()) {
//                markdown.append(import_).append("\n");
//            }
//            markdown.append("```\n\n");
//        }
//
//        // 添加完整代码
//        markdown.append("### Full Code\n\n```go\n")
//                .append(functionInfo.getCode())
//                .append("\n```\n");
//
//        return markdown.toString();
//    }
//}