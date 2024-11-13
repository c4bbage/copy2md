在jetbrains 插件上继续基于FunctionCallAnalyzer接口实现适用于python的函数复制 go的函数复制，要求不要引入特殊的依赖例如com.intellij.modules.go python ？针对现有的JavaFunctionCallAnalyzer代码进行【分析代码思路
】实现python go的函数调用关系复制功能。
implements FunctionCallAnalyzer 请一步一步的思考，并回答我完整、正确的代码，不要省略细节

# 遵循以下原则：
提供完整、可运行的代码，不会省略关键部分
遵循最佳编程实践和设计模式
代码会包含必要的错误处理和边界条件检查
适当添加代码注释来解释关键逻辑
使用规范的命名和格式化
如有必要会提供单元测试
指出潜在的性能和安全考虑

# 分析代码思路
分析代码的整体架构和功能细节
说明已经实现细节和注重点特殊处理
解释关键实现细节Project Name: copy2md