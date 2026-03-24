package com.viyable.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class AmountToWords {

    private static final String[] units = {
            "", "One", "Two", "Three", "Four", "Five",
            "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen",
            "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    };

    private static final String[] tens = {
            "", "", "Twenty", "Thirty", "Forty",
            "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    // Convert number below 1000
    private static String convertBelowThousand(int number) {
        StringBuilder words = new StringBuilder();

        if (number >= 100) {
            words.append(units[number / 100]).append(" Hundred ");
            number %= 100;
        }

        if (number > 0) {
            if (number < 20) {
                words.append(units[number]);
            } else {
                words.append(tens[number / 10]);
                if (number % 10 != 0) {
                    words.append(" ").append(units[number % 10]);
                }
            }
        }

        return words.toString().trim();
    }

    public static String convert(double amount) {
        BigDecimal bd = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);

        long rupees = bd.longValue();
        int paise = bd
                .subtract(BigDecimal.valueOf(rupees))
                .movePointRight(2)
                .intValue();

        if (rupees == 0 && paise == 0) {
            return "Zero Rupees Only";
        }

        StringBuilder words = new StringBuilder();

        // Indian Numbering System
        long crore = rupees / 10000000;
        rupees = rupees % 10000000;

        long lakh = rupees / 100000;
        rupees = rupees % 100000;

        long thousand = rupees / 1000;
        rupees = rupees % 1000;

        long hundred = rupees;

        if (crore > 0) {
            words.append(convertBelowThousand((int) crore)).append(" Crore ");
        }
        if (lakh > 0) {
            words.append(convertBelowThousand((int) lakh)).append(" Lakh ");
        }
        if (thousand > 0) {
            words.append(convertBelowThousand((int) thousand)).append(" Thousand ");
        }
        if (hundred > 0) {
            words.append(convertBelowThousand((int) hundred)).append(" ");
        }

        words.append("Rupees");

        if (paise > 0) {
            words.append(" and ");
            words.append(convertBelowThousand(paise)).append(" Paise");
        }

        words.append(" Only");

        return words.toString().replaceAll("\\s+", " ").trim();
    }

    // Testing
    public static void main(String[] args) {
        System.out.println(convert(0));
        System.out.println(convert(10));
        System.out.println(convert(125.50));
        System.out.println(convert(100000.75));
        System.out.println(convert(12345678.90));
    }
}

