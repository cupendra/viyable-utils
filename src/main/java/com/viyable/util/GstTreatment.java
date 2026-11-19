package com.viyable.util;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Why a supply carries the GST it does — the fact a rate cannot tell.
 *
 * <p><b>⚠ A 0% rate does not say WHICH 0%.</b> GSTR-1 Table 8 has separate columns for nil-rated, exempted and
 * non-GST supplies, and they are not interchangeable: live plants are EXEMPT (Notification 10/2025-CT(R), which
 * superseded 2/2017 from 22 Sep 2025), a nil-rated good is one notified at 0%, and alcohol for drinking or petrol is
 * outside GST altogether. So the treatment is stated per product by the firm and copied onto every line at issue,
 * never inferred from {@code rate == 0} (ACCOUNTING_PLAN §17.13.3 F2/O3).
 *
 * <p>{@link #NOT_STATED} is its own value, never NULL: a 0% product the firm has not answered for. It is listed on
 * the return as needing attention rather than guessed into a column.
 */
public enum GstTreatment {
    TAXABLE("Taxable"),
    NIL_RATED("Nil-rated"),
    EXEMPT("Exempt"),
    NON_GST("Outside GST"),
    NOT_STATED("Not stated");

    private final String label;

    GstTreatment(String label) {
        this.label = label;
    }

    /** The word shown to a person. */
    public String label() {
        return label;
    }

    /** Whether tax is charged on it at all. */
    public boolean taxable() {
        return this == TAXABLE;
    }

    /** The treatment for a stored value (any case), or null when it is blank or not one of ours. */
    public static GstTreatment of(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException notOne) {
            return null;
        }
    }

    /**
     * The treatment to store for {@code stated} at {@code rate}. A rate above 0% admits only one answer, TAXABLE,
     * so an unstated treatment there is settled to it (an old screen or an import that does not send the field
     * still saves correctly). Nothing else is settled: at 0% the firm must say which 0% it is.
     */
    public static GstTreatment settle(GstTreatment stated, BigDecimal rate) {
        if (positive(rate) && (stated == null || stated == NOT_STATED)) return TAXABLE;
        return stated == null ? NOT_STATED : stated;
    }

    /**
     * The treatment of an order or invoice LINE, from its product's treatment and the rate the line was actually
     * CHARGED at — the charge is the truth on a document. Charged above 0% → TAXABLE, whatever the product says now.
     * Charged at 0% → the product's own non-taxable answer (nil-rated / exempt / outside GST), else NOT_STATED: a 0%
     * line is never assumed exempt, and a product that says TAXABLE but was charged nothing is not stated either.
     */
    public static GstTreatment forLine(GstTreatment product, BigDecimal chargedRate) {
        if (positive(chargedRate)) return TAXABLE;
        if (product == null || product == TAXABLE) return NOT_STATED;
        return product;
    }

    /**
     * What is wrong with this treatment at this rate, as a sentence, or null. Call it on the SETTLED value.
     * TAXABLE needs a rate above 0%; nil-rated, exempt and outside-GST need 0%; a 0% product must say which.
     */
    public static String problem(GstTreatment treatment, BigDecimal rate) {
        GstTreatment t = treatment == null ? NOT_STATED : treatment;
        boolean charged = positive(rate);
        if (t == TAXABLE && !charged) {
            return "A taxable product needs a GST rate above 0%. If no GST applies, say whether it is nil-rated, exempt or outside GST.";
        }
        if (t != TAXABLE && t != NOT_STATED && charged) {
            String what = switch (t) {
                case NIL_RATED -> "A nil-rated product";
                case EXEMPT -> "An exempt product";
                default -> "A product outside GST";
            };
            return what + " carries no GST, so its rate must be 0%.";
        }
        if (t == NOT_STATED && !charged) {
            return "Say why this product carries no GST: nil-rated, exempt or outside GST.";
        }
        return null;
    }

    private static boolean positive(BigDecimal rate) {
        return rate != null && rate.signum() > 0;
    }
}
