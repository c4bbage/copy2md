package com.bf.copy2md.core.extractors;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.util.HashMap;
import java.util.Map;

public class ExtractorFactory {
    private static final Map<String, LanguageExtractor> extractors = new HashMap<>();
    private static final Logger LOG = Logger.getInstance(ExtractorFactory.class);

    public static LanguageExtractor getExtractor(String fileExtension, Project project) {
        if (extractors.containsKey(fileExtension)) {
            return extractors.get(fileExtension);
        }

        LanguageExtractor extractor = createExtractor(fileExtension, project);
        if (extractor != null) {
            extractors.put(fileExtension, extractor);
        }
        return extractor;
    }

    private static LanguageExtractor createExtractor(String fileExtension, Project project) {
        switch (fileExtension.toLowerCase()) {
            case "py":
                return new PythonExtractor(project);
            case "java":
                return new JavaExtractor(project);
            case "go":
                return new GoExtractor(project);
            default:
                LOG.warn("No extractor found for file extension: " + fileExtension);
                return null;
        }
    }
}
