package bf.com.copy2md.analysis;

import bf.com.copy2md.model.FunctionContext;
import com.intellij.psi.PsiElement;
import java.util.Set;

public interface FunctionCallAnalyzer {
    Set<FunctionContext> analyzeFunctionCalls(PsiElement function);
}