package com.bf.copy2md.core.extractors;

public class ExtractorException extends RuntimeException {
    public enum ErrorType {
        SUPER_METHOD_NOT_FOUND("Super method implementation not found"),
        CROSS_FILE_DEPENDENCY("Cross-file dependency not found"),
        DECORATOR_ERROR("Error processing decorator"),
        INTERFACE_METHOD("Interface method cannot be extracted"),
        ABSTRACT_METHOD("Abstract method cannot be extracted"),
        GENERIC_METHOD_ERROR("Error processing generic method"),
        LAMBDA_EXPRESSION("Lambda expression cannot be extracted"),
        ANONYMOUS_CLASS("Anonymous class method cannot be extracted"),
        CIRCULAR_DEPENDENCY("Circular dependency detected"),
        INVALID_SYNTAX("Invalid syntax in source code"),
        UNKNOWN_ERROR("Unknown error occurred");

        private final String message;

        ErrorType(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    private final ErrorType errorType;
    private final String details;

    public ExtractorException(ErrorType errorType, String details) {
        super(errorType.getMessage() + (details != null ? ": " + details : ""));
        this.errorType = errorType;
        this.details = details;
    }

    public ExtractorException(ErrorType errorType) {
        this(errorType, null);
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public String getDetails() {
        return details;
    }
}
