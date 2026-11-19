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

    /**
     * The GST code of a place of supply as documents have stored it: a bare code ("36", "7"), a name ("Telangana"),
     * a name with its code ("Telangana (36)") or the return's own form ("36-Telangana"). When a code and a name are
     * both written they must agree, else null — a document saying "Karnataka (36)" is not placed by guessing which
     * half is right. Null when nothing maps.
     */
    public static String placeCode(String written) {
        if (written == null || written.isBlank()) return null;
        String t = written.trim();
        java.util.regex.Matcher m = CODE_IN_TEXT.matcher(t);
        String code = null;
        String name = t;
        if (t.matches("\\d{1,2}")) {
            code = t;
            name = null;
        } else if (m.matches()) {
            code = m.group(2) != null ? m.group(2) : m.group(3);
            name = m.group(2) != null ? m.group(1) : m.group(4);
        }
        if (code != null) {
            code = code.length() == 1 ? "0" + code : code;
            if (!LABEL_BY_CODE.containsKey(code)) return null;
            if (name == null || name.isBlank()) return code;
            String named = codeOf(name);
            return named == null || named.equals(code) ? code : null;
        }
        return codeOf(name);
    }

    /** "Name (36)" or "36-Name" / "36 - Name". */
    private static final java.util.regex.Pattern CODE_IN_TEXT =
            java.util.regex.Pattern.compile("^(.*?)\\s*\\(\\s*(\\d{1,2})\\s*\\)\\s*$|^(\\d{1,2})\\s*-\\s*(.+)$");

    /**
     * The place-of-supply string GSTN's GSTR-1 template accepts for a code, byte for byte ("36-Telangana",
     * "31-Lakshdweep" sic, "26-Dadra & Nagar Haveli & Daman & Diu"), or null for an unknown code. From the
     * template V2.2 {@code master} sheet; "96-Foreign Country" is the e-commerce/export list's extra entry.
     */
    public static String templateLabel(String code) {
        if (code == null) return null;
        String c = code.trim();
        if (c.length() == 1) c = "0" + c;
        return LABEL_BY_CODE.get(c);
    }

    private static final Map<String, String> LABEL_BY_CODE = new HashMap<>();

    static {
        String[] labels = {
                "01-Jammu & Kashmir", "02-Himachal Pradesh", "03-Punjab", "04-Chandigarh", "05-Uttarakhand",
                "06-Haryana", "07-Delhi", "08-Rajasthan", "09-Uttar Pradesh", "10-Bihar", "11-Sikkim",
                "12-Arunachal Pradesh", "13-Nagaland", "14-Manipur", "15-Mizoram", "16-Tripura", "17-Meghalaya",
                "18-Assam", "19-West Bengal", "20-Jharkhand", "21-Odisha", "22-Chhattisgarh", "23-Madhya Pradesh",
                "24-Gujarat", "25-Daman & Diu", "26-Dadra & Nagar Haveli & Daman & Diu", "27-Maharashtra",
                "29-Karnataka", "30-Goa", "31-Lakshdweep", "32-Kerala", "33-Tamil Nadu", "34-Puducherry",
                "35-Andaman & Nicobar Islands", "36-Telangana", "37-Andhra Pradesh", "38-Ladakh",
                "96-Foreign Country", "97-Other Territory"};
        for (String l : labels) LABEL_BY_CODE.put(l.substring(0, 2), l);
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
