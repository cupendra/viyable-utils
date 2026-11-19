package com.viyable.util;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The GSTR-1 section an outward document is reported in, and the ONE rule that decides it.
 *
 * <p><b>⚠ Decided once, at issue, and frozen on the document</b> (OMS {@code invoice_details.gstr1_section},
 * ACCOUNTING_PLAN §17.13.3 O1). A credit or debit note inherits its invoice's section rather than being classified
 * again: the B2CL limit moved from ₹2.5 lakh to ₹1 lakh in Aug 2024, so a note re-classified on today's rule could land
 * in a table its invoice never reached. Re-deriving later is how a return stops agreeing with the one filed.
 *
 * <p>The procedure (GSTN template V2.2 help, Rule 59(4) as amended by Notif 12/2024-CT):
 * <pre>
 * buyer has a GSTIN/UIN                                  → B2B
 * else place of supply = seller's state                  → B2CS, any value
 * else invoice value (incl. tax) MORE THAN the limit     → B2CL   (limit 1,00,000 from Aug 2024, 2,50,000 before)
 * else                                                   → B2CS
 * </pre>
 * Exports, SEZ and deemed exports exist as sections but no document records the facts that decide them (export
 * with/without payment, an SEZ unit), so {@link #of} never returns them; a foreign place of supply ("96") comes back
 * unclassified, to be listed — never filed as a domestic sale.
 */
public enum Gstr1Section {
    B2B(true), B2CL(false), B2CS(false), EXPWP(false), EXPWOP(false), SEZWP(true), SEZWOP(true), DE(true);

    /** From the Aug 2024 return period the B2CL limit is ₹1 lakh (Notif 12/2024-CT, Rule 59(4)). */
    public static final LocalDate LOWER_B2CL_LIMIT_FROM = LocalDate.of(2024, 8, 1);
    public static final BigDecimal B2CL_LIMIT = new BigDecimal("100000.00");
    public static final BigDecimal B2CL_LIMIT_BEFORE_AUG_2024 = new BigDecimal("250000.00");

    /** The place-of-supply code of a supply outside India. */
    public static final String FOREIGN = "96";

    private final boolean registeredTab;

    Gstr1Section(boolean registeredTab) {
        this.registeredTab = registeredTab;
    }

    /**
     * Whether its lines belong on Table 12's {@code hsn(b2b)} tab (supplies to registered persons, SEZ, deemed
     * exports) rather than {@code hsn(b2c)} (B2CL, B2CS and exports). GSTN advisory 1 May 2025.
     */
    public boolean onHsnB2bTab() {
        return registeredTab;
    }

    /** Whether the buyer is a registered person, for Table 8's rows. */
    public boolean toRegisteredPerson() {
        return registeredTab;
    }

    /** The B2CL limit for a document dated {@code day}: the value must be MORE THAN this. */
    public static BigDecimal b2clLimit(LocalDate day) {
        return day != null && day.isBefore(LOWER_B2CL_LIMIT_FROM) ? B2CL_LIMIT_BEFORE_AUG_2024 : B2CL_LIMIT;
    }

    /**
     * The section of a supply, or null when it cannot be decided: the place of supply is unknown (never the seller's
     * own state by guess), the seller's state is unknown, or the place is outside India.
     *
     * @param buyerGstin       the buyer's GSTIN or UIN as frozen on the document; blank = unregistered
     * @param sellerStateCode  the seller's GST state code (its GSTIN's first two digits)
     * @param placeOfSupply    the place-of-supply state code, two digits
     * @param invoiceValue     the whole document's value including tax and round-off
     * @param documentDay      the document's date, in the firm's day
     */
    public static Gstr1Section of(String buyerGstin, String sellerStateCode, String placeOfSupply,
                                  BigDecimal invoiceValue, LocalDate documentDay) {
        String pos = twoDigits(placeOfSupply);
        String seller = twoDigits(sellerStateCode);
        if (pos == null || seller == null || FOREIGN.equals(pos)) return null;
        if (buyerGstin != null && !buyerGstin.isBlank()) return B2B;
        if (pos.equals(seller)) return B2CS;
        BigDecimal value = invoiceValue == null ? BigDecimal.ZERO : invoiceValue;
        return value.compareTo(b2clLimit(documentDay)) > 0 ? B2CL : B2CS;
    }

    /** Whether a supply from {@code sellerStateCode} to {@code placeOfSupply} crosses a state line (IGST). */
    public static boolean interstate(String sellerStateCode, String placeOfSupply) {
        String pos = twoDigits(placeOfSupply);
        String seller = twoDigits(sellerStateCode);
        return pos != null && seller != null && !pos.equals(seller);
    }

    /** The section for a stored value, or null when blank or not one of ours. */
    public static Gstr1Section parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim());
        } catch (IllegalArgumentException notOne) {
            return null;
        }
    }

    private static String twoDigits(String code) {
        if (code == null) return null;
        String t = code.trim();
        if (t.isEmpty() || !t.chars().allMatch(Character::isDigit) || t.length() > 2) return null;
        return t.length() == 1 ? "0" + t : t;
    }
}
