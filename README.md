# Copy2MD - JetBrains IDE Plugin

A powerful JetBrains IDE plugin for copying code with dependencies and formatting it into Markdown, supporting multiple programming languages including Python, Java, and Go.

## Features

### 1. Copy Code with Dependencies
- **Smart Dependency Detection**: Automatically detects and includes function dependencies
- **Multi-language Support**: Works with Python, Java, and Go
- **Recursive Analysis**: Tracks nested function calls and dependencies
- **Configurable Depth**: Control how deep the dependency analysis goes

### 2. Copy to Markdown Actions
- **Copy Selection → Markdown**: Copy selected code snippet with proper formatting
- **Copy File → Markdown**: Copy entire file(s) with proper formatting
  - Works in editor context menu
  - Supports multiple file selection in project view
  - Automatically skips directories
  - Maintains code structure and formatting

### 3. Advanced Features
- **Language Detection**: Automatically detects and applies correct language syntax
- **Project Context**: Includes project and file information in output
- **Error Handling**: Graceful handling of unsupported files and formats
- **Performance Optimized**: Caching mechanism for better performance

## Usage

### Copy Selection to Markdown
1. Select code in editor
2. Right-click → Copy Selection → Markdown
   - Or use keyboard shortcut
3. Paste formatted code anywhere

### Copy File(s) to Markdown
1. Method 1: From Editor
   - Right-click in editor
   - Select "Copy File → Markdown"

2. Method 2: From Project View
   - Select one or more files
   - Right-click → Copy File → Markdown
   - Works with multiple files at once

## Installation
1. Open JetBrains IDE (IntelliJ IDEA, PyCharm, GoLand, etc.)
2. Go to Settings/Preferences → Plugins
3. Search for "Copy2MD"
4. Click Install
5. Restart IDE

## Requirements
- JetBrains IDE version 2020.3 or later
- Java 17 or later

## Technical Details
- Built on IntelliJ Platform SDK
- Uses PSI (Program Structure Interface) for code analysis
- Gradle-based build system

## Contributing
We welcome contributions! Please feel free to submit a Pull Request.

## License
This project is licensed under the Apache 2.0 License - see the LICENSE file for details.

## Support
If you encounter any issues or have suggestions, please file an issue on our GitHub repository.

---

# Development Notes

## Language-Specific Implementation Details

### Python Function Analysis
- **Function Detection Challenges**
  - Regular functions (`def`) vs Async functions (`async def`)
  - Decorated functions require special handling
    * Need to track decorator chain
    * Handle built-in decorators (@property, @classmethod, etc.)
    * Support custom decorators
  - Class methods vs Static methods vs Instance methods
  - Nested functions and closures
  - Lambda functions
- **Dependency Resolution**
  - Import statement analysis (absolute vs relative imports)
  - Handle circular imports
  - Package/module resolution
  - Dynamic imports (importlib)
- **Special Cases**
  - Generator functions (yield statements)
  - Context managers (with statements)
  - Property decorators
  - Magic methods (__init__, __call__, etc.)

### Java Method Analysis
- **Method Types and Complexity**
  - Regular methods with different access modifiers
  - Constructors (including overloaded)
  - Abstract methods in interfaces/abstract classes
  - Native methods requiring JNI
  - Default interface methods (Java 8+)
  - Static and instance initialization blocks
- **Type System Handling**
  - Generic methods with type parameters
  - Bounded type parameters (extends/super)
  - Type erasure considerations
  - Raw types compatibility
- **Inheritance and Polymorphism**
  - Method overriding vs overloading
  - Interface implementation
  - Abstract class extension
  - Bridge methods
- **Annotations Processing**
  - Built-in annotations (@Override, @Deprecated)
  - Custom annotations
  - Runtime vs Compile-time annotations

### Go Function Analysis
- **Function Categories**
  - Package-level functions
  - Methods with receivers (value vs pointer)
  - Interface methods
  - Anonymous functions and closures
- **Go-Specific Features**
  - Multiple return values
  - Named return parameters
  - Defer statements
  - Panic/Recover handling
- **Type System**
  - Interface satisfaction
  - Structural typing
  - Type embedding
  - Type assertions and switches
- **Concurrency Patterns**
  - Goroutine functions
  - Channel operations
  - Select statements
  - Sync package usage

## Cross-Language Function Calls

### Call Graph Construction
- **Language Bridges**
  - JNI for Java-Native calls
  - CGo for Go-C interaction
  - Python C extensions
- **Call Detection Strategies**
  - Static analysis for each language
  - Dynamic call resolution
  - Interface method resolution
  - Virtual method table analysis

### Dependency Resolution
- **Import Analysis**
  - Language-specific import syntax
  - Package management systems
    * pip for Python
    * Maven/Gradle for Java
    * Go modules
  - Version compatibility
- **Cross-Language Dependencies**
  - Foreign function interfaces
  - Inter-process communication
  - Network service calls
  - Shared library usage

## Result Processing Pipeline

### 1. Function Extraction Phase
- Parse source code using language-specific PSI
- Extract function signatures and bodies
- Build symbol tables
- Resolve name bindings
- Handle scope rules

### 2. Dependency Analysis Phase
- Construct call graphs
- Resolve cross-language calls
- Track function usage patterns
- Handle circular dependencies
- Cache results for performance

### 3. Output Generation Phase
- Format extracted code
- Generate dependency documentation
- Create visual representations
- Support multiple output formats
  * Markdown
  * HTML
  * JSON/XML
  * PlantUML

## Performance Optimizations

### Caching Strategy
- Cache parsed ASTs
- Memoize function definitions
- Store resolved dependencies
- Implement LRU cache for frequently accessed items

### Memory Management
- Limit recursion depth
- Implement lazy loading
- Use weak references for large objects
- Clean up unused cache entries

### Concurrent Processing
- Parallel file parsing
- Async dependency resolution
- Thread pool for heavy operations
- Non-blocking UI updates

## Future Development

### Planned Improvements
1. Additional Language Support
   - TypeScript/JavaScript
   - C/C++
   - Rust
   - Kotlin

2. Enhanced Analysis Features
   - Data flow analysis
   - Control flow graphs
   - Call hierarchy visualization
   - Dead code detection

3. Performance Enhancements
   - Incremental analysis
   - Smarter caching
   - Parallel processing
   - Memory optimization

4. UI/UX Improvements
   - Custom formatting options
   - Interactive dependency graphs
   - Better error reporting
   - Progress indicators

## Contributing Guidelines
We welcome contributions! Please feel free to submit a Pull Request.