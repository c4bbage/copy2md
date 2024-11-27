package com.bf.copy2md.action;

import com.bf.copy2md.core.FunctionExtractor;
import com.bf.copy2md.formatter.MarkdownFormatter;
import com.bf.copy2md.util.CopyUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class CopyFunctionAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(CopyFunctionAction.class);
    private final MarkdownFormatter formatter = new MarkdownFormatter();
    private FunctionExtractor extractor;
    private boolean debug = false;

    public CopyFunctionAction() {
        super();
        this.debug = Boolean.getBoolean("copy2md.debug");
    }

    private FunctionExtractor getExtractor(Project project) {
        if (extractor == null) {
            extractor = new FunctionExtractor(project);
            extractor.setDebug(debug);
        }
        return extractor;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        if (debug) {
            LOG.info("CopyFunctionAction.update called");
        }

        // 始终设置为可见
        e.getPresentation().setVisible(true);

        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        if (debug) {
            LOG.info("Initial state - Editor: " + (editor != null ? "not null" : "null") +
                    ", Project: " + (project != null ? "not null" : "null") +
                    ", PsiFile: " + (psiFile != null ? psiFile.getName() : "null"));
        }

        // 如果基本条件不满足，禁用菜单项
        if (project == null || editor == null || psiFile == null) {
            e.getPresentation().setEnabled(false);
            return;
        }

        boolean enabled = false;
        try {
            // 获取当前位置的元素
            int offset = editor.getCaretModel().getOffset();
            PsiElement element = psiFile.findElementAt(offset);
            
            if (debug) {
                LOG.info("Current element at offset " + offset + ": " + 
                        (element != null ? element.getClass().getName() + ", text: " + element.getText() : "null"));
            }

            // 向上遍历查找函数定义
            while (element != null) {
                if (debug) {
                    LOG.info("Checking element: " + element.getClass().getName());
                }

                if (isFunctionOrMethod(element)) {
                    enabled = true;
                    break;
                }
                element = element.getParent();
            }
        } catch (Exception ex) {
            if (debug) {
                LOG.error("Error in update", ex);
            }
            enabled = false;
        }

        e.getPresentation().setEnabled(enabled);
    }

    private boolean isFunctionOrMethod(PsiElement element) {
        if (element == null) return false;

        if (debug) {
            LOG.info("Checking element type: " + element.getClass().getName());
        }

        // 获取元素类名
        String elementClassName = element.getClass().getName();

        // Go 函数和方法
        if (elementClassName.contains("GoFunctionOrMethodDeclaration") ||  // 通用接口
            elementClassName.contains("GoFunctionDeclaration") ||          // 普通函数
            elementClassName.contains("GoMethodDeclaration") ||            // 方法
            elementClassName.contains("GoTypeSpec")) {                     // 类型定义
            if (debug) {
                LOG.info("Found Go function/method: " + elementClassName);
            }
            return true;
        }
        
        // Python 函数和方法
        if (elementClassName.contains("PyFunction") ||           // 普通函数
            elementClassName.contains("PyClass") ||              // 类
            elementClassName.contains("PyDecoratorList") ||      // 装饰器列表
            elementClassName.contains("PyDecorator")) {          // 单个装饰器
            if (debug) {
                LOG.info("Found Python function/method: " + elementClassName);
            }
            return true;
        }
        
        // Java 方法和类
        if (element instanceof PsiMethod ||                      // 普通方法
            elementClassName.contains("PsiClass") ||             // 类
            elementClassName.contains("PsiAnnotationMethod") ||  // 注解方法
            elementClassName.contains("PsiLambdaExpression")) {  // Lambda 表达式
            if (debug) {
                LOG.info("Found Java method: " + elementClassName);
            }
            return true;
        }

        // 检查父元素（向上遍历）
        PsiElement parent = element.getParent();
        while (parent != null) {
            String parentClassName = parent.getClass().getName();
            if (parentClassName.contains("PyFunction") ||        // Python 函数
                parentClassName.contains("PyClass") ||           // Python 类
                parentClassName.contains("PyDecoratorList") ||   // Python 装饰器列表
                parentClassName.contains("PyDecorator") ||       // Python 装饰器
                parentClassName.contains("GoFunctionOrMethodDeclaration") || // Go 函数/方法
                parentClassName.contains("GoTypeSpec")) {        // Go 类型
                if (debug) {
                    LOG.info("Found function/method in parent: " + parentClassName);
                }
                return true;
            }
            parent = parent.getParent();
        }

        // 通过文本内容检查（作为后备方案）
        String text = element.getText().trim();
        boolean result = text.startsWith("func ") ||                      // Go
               text.startsWith("type ") ||                      // Go type
               text.startsWith("def ") ||                       // Python
               text.startsWith("class ") ||                     // Python class
               text.startsWith("async def ") ||                 // Python async
               text.startsWith("@") ||                          // Python 装饰器
               text.matches("^(public|private|protected|static|final|native|synchronized|abstract|transient)\\s+.*"); // Java 修饰符

        if (debug && result) {
            LOG.info("Found function/method by text: " + text.substring(0, Math.min(20, text.length())));
        }

        return result;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        if (debug) {
            LOG.info("CopyFunctionAction.actionPerformed called");
        }

        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);

        if (project == null || editor == null || psiFile == null || virtualFile == null) {
            return;
        }

        try {
            // 获取当前位置的元素
            int offset = editor.getCaretModel().getOffset();
            
            // 使用 FunctionExtractor 提取函数
            FunctionExtractor functionExtractor = getExtractor(project);
            String result = functionExtractor.extractFunctionWithDependencies(psiFile, offset);
            
            if (result != null && !result.isEmpty()) {
                // 使用 MarkdownFormatter 格式化代码
                String markdown = formatter.formatFileContent(project, virtualFile, result);
                // 复制到剪贴板并显示通知
                CopyUtil.copyToClipboardWithNotification(markdown, project);
            } else {
                if (debug) {
                    LOG.warn("No function found at offset: " + offset);
                }
                CopyUtil.showErrorHint(project, "No function found at current position");
            }
        } catch (Exception ex) {
            if (debug) {
                LOG.error("Error in actionPerformed", ex);
            }
            CopyUtil.showErrorHint(project, "Error copying function: " + ex.getMessage());
        }
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
        if (extractor != null) {
            extractor.setDebug(debug);
        }
    }
}
