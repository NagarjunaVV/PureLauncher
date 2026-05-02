package com.example.purelauncher;

import java.util.regex.Pattern;

final class NameValidator {

    private static final Pattern VALID_NAME = Pattern.compile("^[\\p{L}]+(?:\\s+[\\p{L}]+)*$");

    private NameValidator() {
    }

    static String normalize(String rawName) {
        if (rawName == null) {
            return "";
        }
        return rawName.trim().replaceAll("\\s+", " ");
    }

    static boolean isValid(String rawName) {
        String normalized = normalize(rawName);
        if (normalized.isEmpty()) {
            return false;
        }
        return VALID_NAME.matcher(normalized).matches();
    }
}