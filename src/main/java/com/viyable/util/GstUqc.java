package com.viyable.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * GST's Unique Quantity Codes: the 45 units a GSTR-1 HSN summary (Table 12) may state a quantity in.
 *
 * <p>The list and its labels are copied from the {@code master} sheet of GSTN's GSTR-1 Excel template V2.2 (Returns
 * Offline Tool V3.2.4) — {@link #templateLabel()} is the exact dropdown string the tool accepts, e.g.
 * {@code "NOS-NUMBERS"}. There is no "NA": a service (SAC 99…) states no unit at all.
 *
 * <p><b>⚠ A UQC is a fact about the PRODUCT, never read off its printed unit at return time.</b> A product's billing
 * unit is free text ("Nos", "piece", "Pkt", blank) typed per product; {@link #seedFromBillingUnit} turns only the
 * obvious spellings into a code, once, as a starting value (ACCOUNTING_PLAN §17.13.3 F3). Anything else stays unset
 * and is listed for the firm to choose — a guessed unit would put a wrong quantity on a return.
 */
public enum GstUqc {
    BAG("BAGS"), BAL("BALE"), BDL("BUNDLES"), BKL("BUCKLES"), BOU("BILLION OF UNITS"), BOX("BOX"), BTL("BOTTLES"),
    BUN("BUNCHES"), CAN("CANS"), CBM("CUBIC METERS"), CCM("CUBIC CENTIMETERS"), CMS("CENTIMETERS"), CTN("CARTONS"),
    DOZ("DOZENS"), DRM("DRUMS"), GGK("GREAT GROSS"), GMS("GRAMMES"), GRS("GROSS"), GYD("GROSS YARDS"),
    KGS("KILOGRAMS"), KLR("KILOLITRE"), KME("KILOMETRE"), LTR("LITRES"), MLT("MILILITRE"), MTR("METERS"),
    MTS("METRIC TON"), NOS("NUMBERS"), PAC("PACKS"), PCS("PIECES"), PRS("PAIRS"), QTL("QUINTAL"), ROL("ROLLS"),
    SET("SETS"), SQF("SQUARE FEET"), SQM("SQUARE METERS"), SQY("SQUARE YARDS"), TBS("TABLETS"), TGM("TEN GROSS"),
    THD("THOUSANDS"), TON("TONNES"), TUB("TUBES"), UGS("US GALLONS"), UNT("UNITS"), YDS("YARDS"), OTH("OTHERS");

    private final String name;

    GstUqc(String name) {
        this.name = name;
    }

    /** The unit's name as GSTN spells it ("MILILITRE" sic). */
    public String unitName() {
        return name;
    }

    /** The template's dropdown string, byte for byte: {@code "KGS-KILOGRAMS"}. */
    public String templateLabel() {
        return name() + "-" + name;
    }

    /** The code as stored and as the portal's JSON carries it. */
    public String code() {
        return name();
    }

    /** The UQC for a stored code (any case, surrounding space ignored), or null when it is not one of the 45. */
    public static GstUqc of(String code) {
        if (code == null || code.isBlank()) return null;
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException notOne) {
            return null;
        }
    }

    /** What is wrong with a submitted code, as a sentence, or null when it is blank or one of the 45. */
    public static String problem(String code) {
        if (code == null || code.isBlank()) return null;
        return of(code) == null ? "\"" + code.trim() + "\" is not one of GST's unit codes (UQC)." : null;
    }

    private static final Map<String, GstUqc> SEED = new HashMap<>();

    static {
        seed(NOS, "nos", "no", "number", "numbers");
        seed(PCS, "pcs", "pc", "piece", "pieces");
        seed(KGS, "kg", "kgs", "kilogram", "kilograms");
        seed(LTR, "ltr", "ltrs", "litre", "litres", "liter", "liters");
        seed(PAC, "pkt", "pkts", "pack", "packs", "packet", "packets");
        seed(BAG, "bag", "bags");
        seed(BOX, "box", "boxes");
        seed(SET, "set", "sets");
        seed(PRS, "pair", "pairs");
        seed(BTL, "bot", "btl", "bottle", "bottles");
    }

    private static void seed(GstUqc uqc, String... spellings) {
        for (String s : spellings) SEED.put(s, uqc);
    }

    /**
     * The starting UQC for a product whose billing unit is typed as {@code unit}, from a FIXED table of plain
     * spellings ("Nos", "piece", "Pkt", "Kg", "Ltr", "Bag", "Box", "Set", "Pair", "bot" and their plurals);
     * null for anything else, including blank. A seed only — the firm's own choice on the product wins.
     */
    public static GstUqc seedFromBillingUnit(String unit) {
        if (unit == null) return null;
        String key = unit.trim().toLowerCase(Locale.ROOT);
        if (key.endsWith(".")) key = key.substring(0, key.length() - 1).trim();
        return SEED.get(key);
    }
}
