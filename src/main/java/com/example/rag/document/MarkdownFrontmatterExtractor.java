package com.example.rag.document;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class MarkdownFrontmatterExtractor {

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
            "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)", Pattern.DOTALL);

    public static boolean hasFrontmatter(String content) {
        return content != null && content.startsWith("---");
    }

    public static String extractBody(String content) {
        Matcher m = FRONTMATTER_PATTERN.matcher(content);
        if (m.find()) {
            return m.group(2).trim();
        }
        return content;
    }

    public static Map<String, String> extractYaml(String content) {
        Map<String, String> result = new LinkedHashMap<>();
        if (content == null || !content.trim().startsWith("---")) {
            return result;
        }
        int endIdx = content.indexOf("---", 3);
        if (endIdx < 0) return result;

        String yamlBlock = content.substring(3, endIdx).trim();
        Pattern entryPattern = Pattern.compile("^([a-zA-Z_\\u4e00-\\u9fff][a-zA-Z0-9_\\u4e00-\\u9fff]*)\\s*:\\s*(.+)$");
        Pattern listItemPattern = Pattern.compile("^\\s*-\\s*(.+)$");

        String currentKey = null;
        StringBuilder currentListValue = null;

        for (String line : yamlBlock.split("\\n")) {
            Matcher entryMatcher = entryPattern.matcher(line);
            Matcher listMatcher = listItemPattern.matcher(line);

            if (listMatcher.matches() && currentKey != null) {
                if (currentListValue == null) {
                    currentListValue = new StringBuilder();
                }
                if (currentListValue.length() > 0) {
                    currentListValue.append("||");
                }
                currentListValue.append(listMatcher.group(1).trim());
            } else if (entryMatcher.matches()) {
                if (currentKey != null && currentListValue != null) {
                    result.put(currentKey, currentListValue.toString());
                }
                currentKey = entryMatcher.group(1);
                String value = entryMatcher.group(2).trim();
                currentListValue = null;
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                if (!value.isEmpty() && !value.equals("[]")) {
                    result.put(currentKey, value);
                    currentKey = null;
                }
            }
        }
        if (currentKey != null && currentListValue != null) {
            result.put(currentKey, currentListValue.toString());
        }
        return result;
    }

    public static CarDocumentMetadata extractCarMetadata(String content) {
        Map<String, String> yaml = extractYaml(content);
        CarDocumentMetadata.CarDocumentMetadataBuilder builder = CarDocumentMetadata.builder();

        builder.brand(getString(yaml, "brand"))
               .model(getString(yaml, "model"))
               .series(getString(yaml, "series"))
               .year(getString(yaml, "year"))
               .priceRange(getString(yaml, "priceRange"))
               .carType(getString(yaml, "carType"))
               .fuelType(getString(yaml, "fuelType"))
               .targetUsers(getString(yaml, "targetUsers"))
               .salesPoints(getString(yaml, "salesPoints"));

        String tagsStr = yaml.get("tags");
        if (tagsStr != null && !tagsStr.isEmpty()) {
            builder.tags(Arrays.asList(tagsStr.split("\\|\\|")));
        }

        String competitorsStr = yaml.get("competitors");
        if (competitorsStr != null && !competitorsStr.isEmpty()) {
            builder.competitors(Arrays.asList(competitorsStr.split("\\|\\|")));
        }

        return builder.build();
    }

    private static String getString(Map<String, String> map, String key) {
        String value = map.get(key);
        return value != null ? value : null;
    }
}
