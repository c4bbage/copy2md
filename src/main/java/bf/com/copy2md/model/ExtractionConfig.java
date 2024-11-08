package bf.com.copy2md.model;

public class ExtractionConfig {
    private final boolean includeComments;
    private final boolean includeImports;
    private final boolean includeTests;
    private final int maxDepth;

    public ExtractionConfig(boolean includeComments, boolean includeImports,
                            boolean includeTests, int maxDepth) {
        this.includeComments = includeComments;
        this.includeImports = includeImports;
        this.includeTests = includeTests;
        this.maxDepth = maxDepth;
    }

    // Getters
    public boolean isIncludeComments() { return includeComments; }
    public boolean isIncludeImports() { return includeImports; }
    public boolean isIncludeTests() { return includeTests; }
    public int getMaxDepth() { return maxDepth; }

    // Builder pattern for easier construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean includeComments = true;
        private boolean includeImports = true;
        private boolean includeTests = false;
        private int maxDepth = 3;

        public Builder includeComments(boolean value) {
            this.includeComments = value;
            return this;
        }

        public Builder includeImports(boolean value) {
            this.includeImports = value;
            return this;
        }

        public Builder includeTests(boolean value) {
            this.includeTests = value;
            return this;
        }

        public Builder maxDepth(int value) {
            this.maxDepth = value;
            return this;
        }

        public ExtractionConfig build() {
            return new ExtractionConfig(includeComments, includeImports, includeTests, maxDepth);
        }
    }
}