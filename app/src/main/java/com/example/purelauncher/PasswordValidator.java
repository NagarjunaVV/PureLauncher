package com.example.purelauncher;

import java.util.regex.Pattern;

final class PasswordValidator {

    private static final Pattern HAS_LETTER = Pattern.compile(".*[A-Za-z].*");
    private static final Pattern HAS_NUMBER = Pattern.compile(".*[0-9].*");
    private static final Pattern HAS_SYMBOL = Pattern.compile(".*[^A-Za-z0-9].*");

    private PasswordValidator() {
    }

    static Result validate(String password) {
        String value = password == null ? "" : password;
        Result result = new Result();
        result.hasMinLength = value.length() >= 8;
        result.hasLetter = HAS_LETTER.matcher(value).matches();
        result.hasNumber = HAS_NUMBER.matcher(value).matches();
        result.hasSymbol = HAS_SYMBOL.matcher(value).matches();
        return result;
    }

    static final class Result {
        boolean hasMinLength;
        boolean hasLetter;
        boolean hasNumber;
        boolean hasSymbol;

        boolean isValid() {
            return hasMinLength && hasLetter && hasNumber && hasSymbol;
        }
    }
}
