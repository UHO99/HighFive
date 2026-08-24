package com.mycom.myapp.team5.global.common.util;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 로그·응답에 노출되는 개인정보(이메일, 전화번호, 이름) 마스킹 유틸.
 */
public final class MaskingUtils {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("([A-Za-z0-9._%+-]+)@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(01[016789])[-\\s]?(\\d{3,4})[-\\s]?(\\d{4})");

    private MaskingUtils() {
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() == 1) {
            return "*" + domain;
        }
        return local.charAt(0) + "***" + domain;
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 7) {
            return "****";
        }
        String prefix = digits.substring(0, 3);
        String suffix = digits.substring(digits.length() - 4);
        return prefix + "-****-" + suffix;
    }

    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        if (name.length() == 1) {
            return "*";
        }
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }

    /**
     * 로그용: 인자 문자열 안의 이메일·전화 패턴을 마스킹한다.
     */
    public static String maskForLog(Object value) {
        if (value == null) {
            return "null";
        }
        if (value.getClass().isArray()) {
            return Arrays.stream((Object[]) value)
                    .map(MaskingUtils::maskForLog)
                    .collect(Collectors.joining(", ", "[", "]"));
        }
        String text = String.valueOf(value);
        text = EMAIL_PATTERN.matcher(text).replaceAll(match ->
                maskEmail(match.group(0)));
        text = PHONE_PATTERN.matcher(text).replaceAll(match ->
                maskPhone(match.group(0)));
        return text;
    }
}
