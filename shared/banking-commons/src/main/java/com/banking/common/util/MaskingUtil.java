package com.banking.common.util;

public final class MaskingUtil {

    private MaskingUtil() {}

    /** 9876543210 → 98765XXXXX */
    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 5) return mobile;
        return mobile.substring(0, 5) + "XXXXX";
    }

    /** 2026081500001234 → XXXXXX1234 */
    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) return accountNumber;
        int visibleChars = 4;
        String masked = "X".repeat(accountNumber.length() - visibleChars);
        return masked + accountNumber.substring(accountNumber.length() - visibleChars);
    }

    /** 123456789012 → XXXXXXXX9012 */
    public static String maskAadhaar(String aadhaar) {
        if (aadhaar == null || aadhaar.length() < 4) return aadhaar;
        return "X".repeat(aadhaar.length() - 4) + aadhaar.substring(aadhaar.length() - 4);
    }

    /** priya@example.com → p***@example.com */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return email;
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    /** Replace sensitive values in log strings */
    public static String maskForLog(String text) {
        if (text == null) return null;
        return text
                .replaceAll("\"password\"\\s*:\\s*\"[^\"]+\"", "\"password\":\"***\"")
                .replaceAll("\"pin\"\\s*:\\s*\"[^\"]+\"", "\"pin\":\"***\"")
                .replaceAll("\"otp\"\\s*:\\s*\"[^\"]+\"", "\"otp\":\"***\"")
                .replaceAll("\"cvv\"\\s*:\\s*\"[^\"]+\"", "\"cvv\":\"***\"");
    }
}
