package com.viyable.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * India's GST state codes, and the one rule for whether a supply crosses a state line (IGST) or not
 * (CGST + SGST).
 *
 * <p><b>⚠ Why codes, not names (D10, found 2026-11-02).</b> Invoices decided the tax head by comparing state
 * NAMES as typed: "Odisha" and "Orissa", "Telangana" and "Telangana " or a typo read as two states, and the
 * whole tax moved from CGST + SGST to IGST — on a document the buyer files against. A code is what GST itself
 * uses (a GSTIN's first two digits).
 */
public final class GstStates {

    private GstStates() {}

    private static final Map<String, String> CODE_BY_NAME = new HashMap<>();

    static {
        put("01", "Jammu and Kashmir");
        put("02", "Himachal Pradesh");
        put("03", "Punjab");
        put("04", "Chandigarh");
        put("05", "Uttarakhand", "Uttaranchal");
        put("06", "Haryana");
        put("07", "Delhi", "New Delhi", "NCT of Delhi");
        put("08", "Rajasthan");
        put("09", "Uttar Pradesh");
        put("10", "Bihar");
        put("11", "Sikkim");
        put("12", "Arunachal Pradesh");
        put("13", "Nagaland");
        put("14", "Manipur");
        put("15", "Mizoram");
        put("16", "Tripura");
        put("17", "Meghalaya");
        put("18", "Assam");
        put("19", "West Bengal");
        put("20", "Jharkhand");
        put("21", "Odisha", "Orissa");
        put("22", "Chhattisgarh", "Chattisgarh");
        put("23", "Madhya Pradesh");
        put("24", "Gujarat");
        put("26", "Dadra and Nagar Haveli and Daman and Diu", "Dadra and Nagar Haveli", "Daman and Diu");
        put("27", "Maharashtra");
        put("29", "Karnataka");
        put("30", "Goa");
        put("31", "Lakshadweep");
        put("32", "Kerala");
        put("33", "Tamil Nadu");
        put("34", "Puducherry", "Pondicherry");
        put("35", "Andaman and Nicobar Islands", "Andaman and Nicobar");
        put("36", "Telangana");
        put("37", "Andhra Pradesh");
        put("38", "Ladakh");
    }

    private static void put(String code, String... names) {
        for (String n : names) CODE_BY_NAME.put(normalise(n), code);
    }

    /** "Jammu & Kashmir", " jammu  and kashmir " and "JAMMU AND KASHMIR" are one name. */
    private static String normalise(String name) {
        return name.toLowerCase(Locale.ROOT).replace("&", " and ").replaceAll("[^a-z ]", " ")
                .replaceAll("\\s+", " ").trim();
    }

    /** The GST code of a state named as people write it, or null when it is not one we know. */
    public static String codeOf(String stateName) {
        if (stateName == null || stateName.isBlank()) return null;
        return CODE_BY_NAME.get(normalise(stateName));
    }

    /** A GSTIN's state: its first two digits. Null when there is no GSTIN or it does not start with two digits. */
    public static String codeOfGstin(String gstin) {
        if (gstin == null) return null;
        String g = gstin.trim();
        return g.length() >= 2 && Character.isDigit(g.charAt(0)) && Character.isDigit(g.charAt(1)) ? g.substring(0, 2) : null;
    }

    /**
     * Whether a supply is inter-state, decided on CODES: a stored code first; for the SELLER, the state of its
     * GSTIN next (where it is registered is where it supplies from); then the name mapped to its code.
     *
     * <p>⚠ The BUYER's GSTIN is deliberately not used: for goods the place of supply is where they are
     * DELIVERED, which is the ship-to address, not where the buyer is registered.
     *
     * <p>When either side cannot be placed, the answer is intra-state — what every document did before when
     * a state was missing — rather than a guess at IGST.
     */
    public static boolean interstate(String sellerCode, String sellerGstin, String sellerState,
                                     String placeOfSupplyCode, String placeOfSupplyState) {
        String seller = firstCode(sellerCode, codeOfGstin(sellerGstin), codeOf(sellerState));
        String buyer = firstCode(placeOfSupplyCode, null, codeOf(placeOfSupplyState));
        if (seller != null && buyer != null) return !seller.equals(buyer);
        // Neither side maps to a known code (a foreign or unusual address): compare what was written.
        if (sellerState != null && placeOfSupplyState != null && !sellerState.isBlank() && !placeOfSupplyState.isBlank()) {
            return !normalise(sellerState).equals(normalise(placeOfSupplyState));
        }
        return false;
    }

    private static String firstCode(String... codes) {
        for (String c : codes) {
            if (c != null && !c.isBlank()) {
                String t = c.trim();
                return t.length() == 1 ? "0" + t : t;
            }
        }
        return null;
    }
}
