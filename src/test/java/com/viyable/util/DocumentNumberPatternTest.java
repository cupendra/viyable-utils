package com.viyable.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The grammar of a document number (ACCOUNTING_PLAN.md §17.13.9).
 *
 * <p>⚠ The catalogue below is TEST DATA, written to match what the owner decided (§17.13.6–8). The real catalogue is
 * firmland's ({@code document_type}, {@code number_format_pattern}); firmland runs the same 16-character proof over its
 * own rows. This test proves the grammar, not the data.
 */
class DocumentNumberPatternTest {

    /** type → {doc code, name}. */
    static final Map<String, String[]> TYPES = new LinkedHashMap<>();
    /** format → type → pattern. */
    static final Map<String, Map<String, String>> FORMATS = new LinkedHashMap<>();

    static {
        TYPES.put("TAX", new String[] {"INV", "Invoice"});
        TYPES.put("SCR", new String[] {"SCR", "Scrap invoice"});
        TYPES.put("CN", new String[] {"CN", "Credit note"});
        TYPES.put("DN", new String[] {"DN", "Debit note"});
        TYPES.put("RV", new String[] {"RV", "Receipt voucher"});
        TYPES.put("DC", new String[] {"DC", "Delivery challan"});
        TYPES.put("SI", new String[] {"SI", "Self-invoice"});
        TYPES.put("RPV", new String[] {"RPV", "Payment voucher"});
        FORMATS.put("TYPE_YEAR", patterns("{firm}/{fy4}/{serial6}", "{doc}/{fy4}/{serial6}"));
        FORMATS.put("COMPACT", patterns("{firm}{fy4}{serial6}", "{doc}{fy4}{serial6}"));
        FORMATS.put("NO_YEAR", patterns("{firm}/{serial6}", "{doc}/{firm}/{serial6}"));
    }

    private static Map<String, String> patterns(String invoice, String others) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String type : TYPES.keySet()) m.put(type, type.equals("TAX") ? invoice : others);
        return m;
    }

    private static String number(String format, String type, String code, String fy, long serial) {
        return DocumentNumberPattern.parse(FORMATS.get(format).get(type)).number(code, TYPES.get(type)[0], fy, serial);
    }

    @Test
    void theDecidedFormatsSpellTheDecidedNumbers() {
        assertEquals("GNTS/2627/000005", number("TYPE_YEAR", "TAX", "GNTS", "2026-27", 5));
        assertEquals("CN/2627/000005", number("TYPE_YEAR", "CN", "GNTS", "2026-27", 5));
        assertEquals("DC/2627/000005", number("TYPE_YEAR", "DC", "GNTS", "2026-27", 5));
        assertEquals("SCR/2627/000005", number("TYPE_YEAR", "SCR", "GNTS", "2026-27", 5));
        assertEquals("RPV/2627/000001", number("TYPE_YEAR", "RPV", "GNTS", "2026-27", 1));
        assertEquals("GNTS2627000005", number("COMPACT", "TAX", "GNTS", "2026-27", 5));
        assertEquals("CN2627000005", number("COMPACT", "CN", "GNTS", "2026-27", 5));
        assertEquals("GNTS/000005", number("NO_YEAR", "TAX", "GNTS", "2026-27", 5));
        assertEquals("CN/GNTS/000005", number("NO_YEAR", "CN", "GNTS", "2026-27", 5));
        assertEquals("2026-27/000042",
                DocumentNumberPattern.parse("{fy7}/{serial6}").number(null, null, "2026-27", 42));
    }

    /** The prefix is the series' identity: everything before the serial. */
    @Test
    void thePrefixIsEverythingBeforeTheSerial() {
        assertEquals("CN/2627/", DocumentNumberPattern.parse("{doc}/{fy4}/{serial6}").prefix("GNTS", "CN", "2026-27"));
        assertEquals("GNTS2627", DocumentNumberPattern.parse("{firm}{fy4}{serial6}").prefix("GNTS", "INV", "2026-27"));
        // NO_YEAR keeps its prefix across years: the year row, not the prefix, restarts the serial.
        DocumentNumberPattern noYear = DocumentNumberPattern.parse("{doc}/{firm}/{serial6}");
        assertEquals(noYear.prefix("GNTS", "CN", "2026-27"), noYear.prefix("GNTS", "CN", "2027-28"));
    }

    /** ⚠ A number that does not fit is never widened or cut: the serial is refused instead. */
    @Test
    void aSerialOutsideTheWidthIsRefusedNeverWidened() {
        DocumentNumberPattern p = DocumentNumberPattern.parse("{doc}/{fy4}/{serial6}");
        assertEquals(999_999, p.maxSerial());
        assertEquals("CN/2627/999999", p.number("X", "CN", "2026-27", 999_999));
        assertThrows(IllegalArgumentException.class, () -> p.number("X", "CN", "2026-27", 1_000_000));
        assertThrows(IllegalArgumentException.class, () -> p.number("X", "CN", "2026-27", 0));
        assertThrows(IllegalArgumentException.class, () -> p.number(null, null, "2026-27", 1)); // {doc} needs a code
        assertThrows(IllegalArgumentException.class, () -> p.number("X", "CN", "2026-28", 1)); // not a real year
    }

    @Test
    void aPatternWithTheWrongShapeIsRefusedInWords() {
        assertNotNull(DocumentNumberPattern.problem(null));
        assertNotNull(DocumentNumberPattern.problem(" "));
        assertTrue(DocumentNumberPattern.problem("{doc}/{serial6}/{fy4}").contains("comes last"));
        assertTrue(DocumentNumberPattern.problem("{doc}/{fy4}").contains("exactly one serial"));
        assertTrue(DocumentNumberPattern.problem("{serial3}{serial3}").contains("exactly one serial"));
        assertTrue(DocumentNumberPattern.problem("INV/{serial6}").contains("\"INV\""));
        assertTrue(DocumentNumberPattern.problem("{doc}/{serial6}x").contains("\"x\""));
        assertTrue(DocumentNumberPattern.problem("{doc}/{serial0}").contains("{serial0}"));
        assertTrue(DocumentNumberPattern.problem("{doc}//{serial6}").contains("two together"));
        assertTrue(DocumentNumberPattern.problem("/{doc}/{serial6}").contains("at the start"));
        assertTrue(DocumentNumberPattern.problem("{doc}/{doc}/{serial6}").contains("repeats"));
        assertTrue(DocumentNumberPattern.problem("{firm}/{firm}/{serial6}").contains("repeats"));
        assertTrue(DocumentNumberPattern.problem("{fy4}{fy7}{serial6}").contains("repeats"));
        assertThrows(IllegalArgumentException.class, () -> DocumentNumberPattern.parse("{doc}/{fy4}"));
        for (Map<String, String> byType : FORMATS.values()) {
            for (String pattern : byType.values()) {
                assertNull(DocumentNumberPattern.problem(pattern), pattern);
            }
        }
    }

    @Test
    void roomForTheFirmCodeIsWhatSixteenLeaves() {
        assertEquals(4, DocumentNumberPattern.parse("{firm}/{fy4}/{serial6}").roomForFirmCode("INV"));
        assertEquals(6, DocumentNumberPattern.parse("{firm}{fy4}{serial6}").roomForFirmCode("INV"));
        assertEquals(6, DocumentNumberPattern.parse("{doc}/{firm}/{serial6}").roomForFirmCode("CN"));
        assertEquals(5, DocumentNumberPattern.parse("{doc}/{firm}/{serial6}").roomForFirmCode("SCR"));
        assertEquals(Integer.MAX_VALUE, DocumentNumberPattern.parse("{doc}/{fy4}/{serial6}").roomForFirmCode("RPV"));
        assertEquals(-5, DocumentNumberPattern.parse("{doc}/{fy7}/{serial9}").roomForFirmCode("SCR"));
        assertEquals(16, DocumentNumberPattern.parse("{firm}/{fy4}/{serial6}").longest(4, "INV"));
        assertFalse(DocumentNumberPattern.parse("{firm}/{fy4}/{serial6}").fits(5, "INV"));
    }

    /**
     * <b>⚠⚠ THE PROMISE OF §17.13.6, CHECKED EXHAUSTIVELY:</b> with the code limit derived from the patterns (the
     * smallest room over every format and type), EVERY number of every format, type and allowed code length, at the
     * first and the last serial and in any year, is at most 16 characters AND accepted by GSTN's own rule. The derived
     * limit for the decided catalogue is the 4 the owner decided.
     */
    @Test
    void everyOfferedFormatFitsForEveryAllowedCode() {
        int limit = Integer.MAX_VALUE;
        for (Map<String, String> byType : FORMATS.values()) {
            for (Map.Entry<String, String> e : byType.entrySet()) {
                limit = Math.min(limit, DocumentNumberPattern.parse(e.getValue()).roomForFirmCode(TYPES.get(e.getKey())[0]));
            }
        }
        assertEquals(4, limit);
        int checked = 0;
        for (String format : FORMATS.keySet()) {
            for (String type : TYPES.keySet()) {
                DocumentNumberPattern p = DocumentNumberPattern.parse(FORMATS.get(format).get(type));
                for (int len = 1; len <= limit; len++) {
                    String code = "ZZZZZZ".substring(0, len);
                    for (String fy : List.of("2026-27", "2099-00")) {
                        for (long serial : new long[] {1, p.maxSerial()}) {
                            String n = p.number(code, TYPES.get(type)[0], fy, serial);
                            assertTrue(n.length() <= DocumentNumberPattern.GST_MAX_LENGTH, n);
                            assertTrue(DocumentNumberPattern.gstnAccepts(n), n);
                            assertTrue(p.fits(len, TYPES.get(type)[0]), n);
                            checked++;
                        }
                    }
                }
            }
        }
        assertEquals(3 * 8 * 4 * 2 * 2, checked);
    }

    /** GSTN's tool rule, as copied from the installed tool: today's prod numbers fail it; the new ones pass. */
    @Test
    void gstnsOwnRuleIsTheOneApplied() {
        assertFalse(DocumentNumberPattern.gstnAccepts("GNTS/2026-27/000005")); // 19: refused by the tool in prod
        assertFalse(DocumentNumberPattern.gstnAccepts("SCR/GNTS/2026-27/000047"));
        assertTrue(DocumentNumberPattern.gstnAccepts("SCR/2627/000047"));
        assertTrue(DocumentNumberPattern.gstnAccepts("a-1/b"));
        assertFalse(DocumentNumberPattern.gstnAccepts("0000")); // all zeros
        assertFalse(DocumentNumberPattern.gstnAccepts("//--"));
        assertFalse(DocumentNumberPattern.gstnAccepts("CN#1"));
        assertFalse(DocumentNumberPattern.gstnAccepts(""));
        assertFalse(DocumentNumberPattern.gstnAccepts(null));
    }

    /** The legend: built from the tokens, with the type's name handed in. */
    @Test
    void theLegendSaysWhatEachPartMeans() {
        List<DocumentNumberPattern.LegendPart> cn = DocumentNumberPattern.parse("{doc}/{fy4}/{serial6}")
                .legend("GNTS", "CN", "Credit note", "2026-27", 5);
        assertEquals(List.of(
                new DocumentNumberPattern.LegendPart("CN", "the code for a credit note"),
                new DocumentNumberPattern.LegendPart("2627", "the financial year 2026-27"),
                new DocumentNumberPattern.LegendPart("000005",
                        "the 5th credit note of the year; numbering starts again at 000001 each 1 April")), cn);

        List<DocumentNumberPattern.LegendPart> inv = DocumentNumberPattern.parse("{firm}/{serial6}")
                .legend("GNTS", "INV", "Invoice", "2026-27", 22);
        assertEquals(new DocumentNumberPattern.LegendPart("GNTS", "your firm's code"), inv.get(0));
        assertEquals("the 22nd invoice of the year; numbering starts again at 000001 each 1 April, so the same number"
                + " comes back every year and the document's date tells them apart", inv.get(1).meaning());
    }

    @Test
    void ordinalsReadAsEnglish() {
        assertEquals("1st", DocumentNumberPattern.ordinal(1));
        assertEquals("2nd", DocumentNumberPattern.ordinal(2));
        assertEquals("3rd", DocumentNumberPattern.ordinal(3));
        assertEquals("4th", DocumentNumberPattern.ordinal(4));
        assertEquals("11th", DocumentNumberPattern.ordinal(11));
        assertEquals("12th", DocumentNumberPattern.ordinal(12));
        assertEquals("13th", DocumentNumberPattern.ordinal(13));
        assertEquals("21st", DocumentNumberPattern.ordinal(21));
        assertEquals("111th", DocumentNumberPattern.ordinal(111));
        assertEquals("102nd", DocumentNumberPattern.ordinal(102));
    }

    @Test
    void whatAPatternCarries() {
        DocumentNumberPattern typeYear = DocumentNumberPattern.parse("{doc}/{fy4}/{serial6}");
        assertFalse(typeYear.carriesFirmCode());
        assertTrue(typeYear.carriesDocCode());
        assertTrue(typeYear.carriesYear());
        assertFalse(DocumentNumberPattern.parse("{firm}/{serial6}").carriesYear());
        assertEquals(6, typeYear.serialDigits());
    }
}
