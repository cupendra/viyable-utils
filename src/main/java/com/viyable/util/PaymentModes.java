package com.viyable.util;

import java.util.Locale;
import java.util.Map;

/**
 * The words forms and old records use for how money moved, and the codes a voucher stores. ⚠ The ONE place they are
 * matched, so a payment made anywhere reads the same in every report.
 *
 * <p>Anything unrecognised is OTHER, never a refusal: hr-service and the portal send their own words, and a refusal there
 * has nobody to read it.
 */
public final class PaymentModes {

    private static final Map<String, String> CODES = Map.ofEntries(
            Map.entry("cash", "CASH"),
            Map.entry("online transfer", "BANK_TRANSFER"),
            Map.entry("bank_transfer", "BANK_TRANSFER"),
            Map.entry("bank transfer", "BANK_TRANSFER"),
            Map.entry("upi", "UPI"),
            Map.entry("card payment", "CARD"),
            Map.entry("card", "CARD"),
            Map.entry("cheque", "CHEQUE"),
            Map.entry("dd", "DD"),
            Map.entry("demand draft", "DD"),
            Map.entry("others", "OTHER"),
            Map.entry("other", "OTHER"));

    private static final Map<String, String> LABELS = Map.of(
            "CASH", "Cash",
            "BANK_TRANSFER", "Online Transfer",
            "UPI", "UPI",
            "CARD", "Card Payment",
            "CHEQUE", "Cheque",
            "DD", "DD",
            "OTHER", "Others");

    private PaymentModes() {
    }

    public static String code(String words) {
        return words == null ? "OTHER" : CODES.getOrDefault(words.trim().toLowerCase(Locale.ROOT), "OTHER");
    }

    public static String label(String code) {
        return LABELS.getOrDefault(code, "Others");
    }
}
