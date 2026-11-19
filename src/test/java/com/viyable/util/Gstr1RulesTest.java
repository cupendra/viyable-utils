package com.viyable.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P4e step (a): the UQC list, the GST treatment rule, the GSTR-1 section classifier, place-of-supply reading. */
class Gstr1RulesTest {

    private static final LocalDate OCT_2026 = LocalDate.of(2026, 10, 15);

    // ---- UQC --------------------------------------------------------------------------------------------------

    @Test
    void thereAreExactlyTheFortyFiveCodesOfTheTemplate() {
        assertEquals(45, GstUqc.values().length);
        assertEquals("NOS-NUMBERS", GstUqc.NOS.templateLabel());
        assertEquals("MLT-MILILITRE", GstUqc.MLT.templateLabel(), "GSTN's own spelling, not corrected");
        assertEquals("OTH-OTHERS", GstUqc.OTH.templateLabel());
        assertTrue(Arrays.stream(GstUqc.values()).allMatch(u -> u.code().length() == 3));
    }

    @Test
    void aStoredCodeIsReadInAnyCaseAndAnythingElseIsAProblem() {
        assertEquals(GstUqc.KGS, GstUqc.of(" kgs "));
        assertNull(GstUqc.of("NA"), "there is no NA: a service states no unit");
        assertNull(GstUqc.problem(""));
        assertNull(GstUqc.problem("bag"));
        assertEquals("\"KG\" is not one of GST's unit codes (UQC).", GstUqc.problem("KG"));
    }

    /** F3: only the plain spellings seed a code; anything else stays unset and is listed — never guessed. */
    @Test
    void theBillingUnitSeedsOnlyFromTheFixedTable() {
        assertEquals(GstUqc.NOS, GstUqc.seedFromBillingUnit("Nos"));
        assertEquals(GstUqc.PCS, GstUqc.seedFromBillingUnit("piece"));
        assertEquals(GstUqc.PCS, GstUqc.seedFromBillingUnit("Pcs."));
        assertEquals(GstUqc.KGS, GstUqc.seedFromBillingUnit("Kg"));
        assertEquals(GstUqc.LTR, GstUqc.seedFromBillingUnit("litre"));
        assertEquals(GstUqc.PAC, GstUqc.seedFromBillingUnit("Pkt"));
        assertEquals(GstUqc.BAG, GstUqc.seedFromBillingUnit("Bag"));
        assertEquals(GstUqc.PRS, GstUqc.seedFromBillingUnit("Pair"));
        assertEquals(GstUqc.BTL, GstUqc.seedFromBillingUnit("bot"));
        assertNull(GstUqc.seedFromBillingUnit(""));
        assertNull(GstUqc.seedFromBillingUnit(null));
        assertNull(GstUqc.seedFromBillingUnit("tray"));
        assertNull(GstUqc.seedFromBillingUnit("g"), "a gram is not guessed from a single letter");
    }

    // ---- GST treatment ----------------------------------------------------------------------------------------

    @Test
    void aRateAboveZeroSettlesAnUnstatedTreatmentToTaxable() {
        assertEquals(GstTreatment.TAXABLE, GstTreatment.settle(null, new BigDecimal("18")));
        assertEquals(GstTreatment.TAXABLE, GstTreatment.settle(GstTreatment.NOT_STATED, new BigDecimal("5")));
        assertEquals(GstTreatment.NOT_STATED, GstTreatment.settle(null, BigDecimal.ZERO), "0% is never settled by guess");
        assertEquals(GstTreatment.EXEMPT, GstTreatment.settle(GstTreatment.EXEMPT, BigDecimal.ZERO));
    }

    @Test
    void theTreatmentMustAgreeWithTheRate() {
        assertNull(GstTreatment.problem(GstTreatment.TAXABLE, new BigDecimal("12")));
        assertNull(GstTreatment.problem(GstTreatment.EXEMPT, BigDecimal.ZERO));
        assertNull(GstTreatment.problem(GstTreatment.NIL_RATED, null));
        assertNotNull(GstTreatment.problem(GstTreatment.TAXABLE, BigDecimal.ZERO));
        assertEquals("An exempt product carries no GST, so its rate must be 0%.",
                GstTreatment.problem(GstTreatment.EXEMPT, new BigDecimal("5")));
        assertEquals("A product outside GST carries no GST, so its rate must be 0%.",
                GstTreatment.problem(GstTreatment.NON_GST, new BigDecimal("5")));
        assertEquals("Say why this product carries no GST: nil-rated, exempt or outside GST.",
                GstTreatment.problem(GstTreatment.NOT_STATED, BigDecimal.ZERO));
        assertEquals(GstTreatment.NON_GST, GstTreatment.of("non_gst"));
        assertNull(GstTreatment.of("ZERO"));
    }

    /** A line's treatment follows what it was CHARGED at; a 0% line is never assumed exempt. */
    @Test
    void aLinesTreatmentFollowsItsCharge() {
        assertEquals(GstTreatment.TAXABLE, GstTreatment.forLine(GstTreatment.EXEMPT, new BigDecimal("5")));
        assertEquals(GstTreatment.TAXABLE, GstTreatment.forLine(null, new BigDecimal("18")));
        assertEquals(GstTreatment.EXEMPT, GstTreatment.forLine(GstTreatment.EXEMPT, BigDecimal.ZERO));
        assertEquals(GstTreatment.NIL_RATED, GstTreatment.forLine(GstTreatment.NIL_RATED, null));
        assertEquals(GstTreatment.NOT_STATED, GstTreatment.forLine(GstTreatment.TAXABLE, BigDecimal.ZERO));
        assertEquals(GstTreatment.NOT_STATED, GstTreatment.forLine(null, BigDecimal.ZERO));
    }

    // ---- GSTR-1 section ---------------------------------------------------------------------------------------

    @Test
    void aBuyerWithAGstinIsB2bWhereverTheGoodsGo() {
        assertEquals(Gstr1Section.B2B, Gstr1Section.of("36AABCU9603R1ZM", "36", "36", new BigDecimal("500"), OCT_2026));
        assertEquals(Gstr1Section.B2B, Gstr1Section.of("29AABCU9603R1ZM", "36", "29", new BigDecimal("5"), OCT_2026));
    }

    @Test
    void anIntraStateSaleToAnUnregisteredBuyerIsB2csAtAnyValue() {
        assertEquals(Gstr1Section.B2CS, Gstr1Section.of(null, "36", "36", new BigDecimal("900000"), OCT_2026));
        assertEquals(Gstr1Section.B2CS, Gstr1Section.of("  ", "36", "36", new BigDecimal("10"), OCT_2026));
    }

    /** "More than" the limit: exactly ₹1,00,000.00 is still B2CS; the limit was ₹2.5 lakh before Aug 2024. */
    @Test
    void anInterStateUnregisteredSaleIsB2clOnlyAboveTheLimitOfItsPeriod() {
        assertEquals(Gstr1Section.B2CS, Gstr1Section.of(null, "36", "29", new BigDecimal("100000.00"), OCT_2026));
        assertEquals(Gstr1Section.B2CL, Gstr1Section.of(null, "36", "29", new BigDecimal("100000.01"), OCT_2026));
        LocalDate july2024 = LocalDate.of(2024, 7, 31);
        assertEquals(Gstr1Section.B2CS, Gstr1Section.of(null, "36", "29", new BigDecimal("150000"), july2024));
        assertEquals(Gstr1Section.B2CL, Gstr1Section.of(null, "36", "29", new BigDecimal("150000"), LocalDate.of(2024, 8, 1)));
    }

    /** A place of supply that is unknown is never taken to be the seller's state: the document is listed instead. */
    @Test
    void anUnknownPlaceOfSupplyIsNotClassified() {
        assertNull(Gstr1Section.of(null, "36", null, new BigDecimal("100"), OCT_2026));
        assertNull(Gstr1Section.of(null, "36", "Telangana", new BigDecimal("100"), OCT_2026), "a name is not a code");
        assertNull(Gstr1Section.of("36AABCU9603R1ZM", null, "36", new BigDecimal("100"), OCT_2026));
        assertNull(Gstr1Section.of(null, "36", "96", new BigDecimal("100"), OCT_2026), "an export is not a domestic sale");
        assertEquals(Gstr1Section.B2CS, Gstr1Section.of(null, "7", "07", BigDecimal.ONE, OCT_2026));
    }

    @Test
    void theHsnTabFollowsTheSection() {
        assertTrue(Gstr1Section.B2B.onHsnB2bTab());
        assertTrue(Gstr1Section.SEZWOP.onHsnB2bTab());
        assertFalse(Gstr1Section.B2CS.onHsnB2bTab());
        assertFalse(Gstr1Section.EXPWP.onHsnB2bTab(), "exports go on the B2C tab (advisory 1 May 2025)");
        assertEquals(Gstr1Section.B2CL, Gstr1Section.parse("B2CL"));
        assertNull(Gstr1Section.parse("b2x"));
    }

    // ---- Place of supply --------------------------------------------------------------------------------------

    @Test
    void aStoredPlaceOfSupplyIsReadInEveryFormDocumentsHold() {
        assertEquals("36", GstStates.placeCode("Telangana"));
        assertEquals("36", GstStates.placeCode("Telangana (36)"));
        assertEquals("36", GstStates.placeCode("36-Telangana"));
        assertEquals("36", GstStates.placeCode("36"));
        assertEquals("07", GstStates.placeCode("7"));
        assertEquals("31", GstStates.placeCode("31-Lakshdweep"), "the template's own misspelling still places it");
        assertNull(GstStates.placeCode(null));
        assertNull(GstStates.placeCode("  "));
        assertNull(GstStates.placeCode("Atlantis"));
        assertNull(GstStates.placeCode("28"), "28 is the retired old Andhra Pradesh code");
    }

    /** A code and a name that disagree are not reconciled by picking one. */
    @Test
    void aCodeAndANameThatDisagreeAreNotPlaced() {
        assertNull(GstStates.placeCode("Karnataka (36)"));
        assertNull(GstStates.placeCode("29-Telangana"));
    }

    @Test
    void theTemplateLabelsAreGstnsExactStrings() {
        assertEquals("36-Telangana", GstStates.templateLabel("36"));
        assertEquals("01-Jammu & Kashmir", GstStates.templateLabel("1"));
        assertEquals("31-Lakshdweep", GstStates.templateLabel("31"));
        assertEquals("26-Dadra & Nagar Haveli & Daman & Diu", GstStates.templateLabel("26"));
        assertEquals("97-Other Territory", GstStates.templateLabel("97"));
        assertNull(GstStates.templateLabel("28"));
    }
}
