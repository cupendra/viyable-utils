package com.viyable.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GstCalculatorTest {

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private static GstCalculator.Line line(String value, String rate) {
        return new GstCalculator.Line(bd(value), rate == null ? null : bd(rate));
    }

    // ── the HSN summary (D17) ───────────────────────────────────────────────

    /**
     * Two lines of one HSN and rate are ONE row; a discount before tax lowers the taxable value; and the
     * rows add up to the document's own GST, CGST and SGST to the paisa.
     */
    @Test
    void theHsnSummaryGroupsByCodeAndRateAndAddsUpToTheDocument() {
        List<GstCalculator.Line> lines = List.of(line("585.00", "5"), line("333.33", "5"), line("199.99", "18"));
        List<String> hsn = List.of("0709", "0709", "9026");
        GstCalculator.Result r = GstCalculator.computeDiscountBeforeTax(lines, bd("50"), false, null);

        List<GstCalculator.HsnRow> rows = GstCalculator.hsnSummary(lines, hsn, true, false, r);

        assertEquals(2, rows.size(), "one row per code and rate");
        assertEquals("0709", rows.get(0).hsn());
        BigDecimal tax = BigDecimal.ZERO, cgst = BigDecimal.ZERO, sgst = BigDecimal.ZERO, taxable = BigDecimal.ZERO;
        for (GstCalculator.HsnRow row : rows) {
            tax = tax.add(row.tax());
            cgst = cgst.add(row.cgst());
            sgst = sgst.add(row.sgst());
            taxable = taxable.add(row.taxable());
            assertEquals(row.tax(), row.cgst().add(row.sgst()));
        }
        assertEquals(r.gstTotal(), tax);
        assertEquals(r.cgst(), cgst);
        assertEquals(r.sgst(), sgst);
        assertEquals(r.itemsSubtotal().subtract(r.discount()), taxable, "the taxable value is what was taxed");
    }

    /** Over many random documents the rows still add up to the document's GST, CGST and IGST exactly. */
    @Test
    void theSummaryAddsUpOverManyRandomDocuments() {
        Random rnd = new Random(7);
        String[] rates = {"5", "12", "18", "28"};
        for (int n = 0; n < 2000; n++) {
            List<GstCalculator.Line> lines = new ArrayList<>();
            List<String> hsn = new ArrayList<>();
            int count = 1 + rnd.nextInt(6);
            for (int i = 0; i < count; i++) {
                lines.add(line(BigDecimal.valueOf(rnd.nextInt(100000), 2).toPlainString(), rates[rnd.nextInt(4)]));
                hsn.add(String.valueOf(1000 + rnd.nextInt(3)));
            }
            boolean inter = rnd.nextBoolean();
            GstCalculator.Result r = GstCalculator.computeDiscountBeforeTax(lines,
                    BigDecimal.valueOf(rnd.nextInt(1000), 2), inter, null);
            BigDecimal tax = BigDecimal.ZERO, cgst = BigDecimal.ZERO, igst = BigDecimal.ZERO;
            for (GstCalculator.HsnRow row : GstCalculator.hsnSummary(lines, hsn, true, inter, r)) {
                tax = tax.add(row.tax());
                cgst = cgst.add(row.cgst());
                igst = igst.add(row.igst());
            }
            assertEquals(r.gstTotal(), tax, "document " + n);
            assertEquals(r.cgst(), cgst, "document " + n);
            assertEquals(r.igst(), igst, "document " + n);
        }
    }

    // ── the two rules ───────────────────────────────────────────────────────

    /**
     * The prod case: ₹585 at 5% printed CGST 14.63 + SGST 14.63 under a GST total of 29.25. The total
     * is computed once at the full rate; the halves are its parts, CGST carrying the odd paisa.
     */
    @Test
    void halvesAddUpToTheTotal() {
        GstCalculator.Result r = GstCalculator.compute(List.of(line("585.00", "5")), null, false, null);
        assertEquals(bd("14.63"), r.cgst());
        assertEquals(bd("14.62"), r.sgst());
        assertEquals(bd("29.25"), r.gstTotal());
        assertEquals(r.cgst().add(r.sgst()), r.gstTotal());
        assertEquals(bd("614.25"), r.grandTotal());
        assertEquals(bd("614.00"), r.roundedGrandTotal());
        assertEquals(bd("-0.25"), r.roundOff());
    }

    /** Where the buyer is changes how the GST is labelled, never how much it is. */
    @Test
    void theTotalIsTheSameWithinAndAcrossStates() {
        for (String v : new String[]{"585.00", "40005.50", "12.25", "999.99"}) {
            GstCalculator.Result intra = GstCalculator.compute(List.of(line(v, "18")), null, false, null);
            GstCalculator.Result inter = GstCalculator.compute(List.of(line(v, "18")), null, true, null);
            assertEquals(intra.gstTotal(), inter.gstTotal(), v);
            assertEquals(intra.payable(), inter.payable(), v);
        }
    }

    // ── discount before tax ─────────────────────────────────────────────────

    /** GST is due on the discounted value: ₹1000 at 18% less ₹100 is tax on ₹900, not on ₹1000. */
    @Test
    void aDiscountBeforeTaxReducesTheTaxableValue() {
        GstCalculator.Result r = GstCalculator.computeDiscountBeforeTax(
                List.of(line("1000.00", "18")), bd("100"), false, null);
        assertEquals(bd("1000.00"), r.itemsSubtotal());
        assertEquals(bd("100.00"), r.discount());
        assertEquals(bd("162.00"), r.gstTotal());
        assertEquals(bd("1062.00"), r.grandTotal());
    }

    /** Spread by value across mixed rates; a delivery line takes none of it and no tax. */
    @Test
    void theDiscountIsSpreadByValueAndNeverOntoDelivery() {
        GstCalculator.Result r = GstCalculator.computeDiscountBeforeTax(List.of(
                line("600.00", "18"), line("400.00", "5"),
                new GstCalculator.Line(bd("49.00"), null, false)), bd("100"), false, null);
        // 600 bears 60, 400 bears 40: 540 × 18% + 360 × 5% = 97.20 + 18.00
        assertEquals(bd("115.20"), r.gstTotal());
        assertEquals(bd("1049.00"), r.itemsSubtotal());
        assertEquals(bd("1064.20"), r.grandTotal());
    }

    @Test
    void sharesAlwaysAddUpToTheAmount() {
        List<GstCalculator.Line> lines = List.of(line("33.33", "5"), line("33.33", "5"), line("33.34", "5"));
        List<BigDecimal> shares = GstCalculator.spread(bd("10.00"), lines);
        assertEquals(bd("10.00"), shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void interstateIsAllIgst() {
        GstCalculator.Result r = GstCalculator.compute(List.of(line("585.00", "5")), null, true, null);
        assertEquals(bd("29.25"), r.igst());
        assertEquals(bd("0.00"), r.cgst());
        assertEquals(bd("0.00"), r.sgst());
        assertEquals(bd("29.25"), r.gstTotal());
    }

    /** TCS is rounded before it joins the total, so the printed lines add up to the printed total. */
    @Test
    void grandTotalIsTheSumOfThePrintedFigures() {
        GstCalculator.Result r = GstCalculator.compute(
                List.of(line("1234.57", "18"), line("99.99", "12"), line("40.00", null)),
                bd("10.00"), false, bd("0.1"));
        BigDecimal printed = r.itemsSubtotal().subtract(r.discount()).add(r.gstTotal()).add(r.tcsAmount());
        assertEquals(printed, r.grandTotal());
        assertEquals(r.grandTotal().add(r.roundOff()), r.roundedGrandTotal());
    }

    @Test
    void untaxedAndZeroRatedLinesCarryNoTax() {
        GstCalculator.Result r = GstCalculator.compute(
                List.of(line("100.00", null), line("50.00", "0")), null, false, null);
        assertEquals(bd("150.00"), r.itemsSubtotal());
        assertEquals(bd("0.00"), r.gstTotal());
        assertNull(r.tcsAmount());
    }

    @Test
    void wholeRupeeTotalHasNoRoundOff() {
        GstCalculator.Result r = GstCalculator.compute(List.of(line("100.00", "18")), null, false, null);
        assertEquals(bd("118.00"), r.grandTotal());
        assertNull(r.roundOff());
        assertNull(r.roundedGrandTotal());
        assertEquals(bd("118.00"), r.payable());
    }

    @Test
    void doubleInputsGoThroughTheirDecimalString() {
        assertEquals(bd("36.30"), GstCalculator.money(3 * 12.1));   // 36.300000000000004 as a double
        assertEquals(bd("2.21"), GstCalculator.paise(bd("2.205"))); // HALF_UP on the exact value
    }

    // ── characterization: nothing else moves ────────────────────────────────

    /**
     * ⚠ THE SAFETY NET FOR THE OMS SWITCH. {@link Legacy} is a verbatim copy of OMS
     * {@code InvoiceBuilderService.populateAmounts} as it stood before this calculator replaced it.
     * Across 20,000 generated documents shaped like real ones, every figure must be identical,
     * except where the old code broke one of the two rules — and there the new figure must be
     * exactly the corrected one, never more than a paisa from the old.
     *
     * <p>Most importantly the AMOUNT PAYABLE must not move: a customer has already paid what
     * checkout charged, and a document that asks for a different rupee is worse than a paisa split.
     */
    @Test
    void matchesTheOldDocumentsExceptWhereTheyBrokeTheRules() {
        Random rnd = new Random(20261102L);
        int payableChanged = 0, payablePaiseOnly = 0, halvesChanged = 0, totalChanged = 0, subtotalChanged = 0, igstChanged = 0;
        String[] rates = {null, "0", "5", "12", "18", "28"};

        for (int n = 0; n < 20_000; n++) {
            int count = 1 + rnd.nextInt(6);
            List<Legacy.Item> legacyItems = new ArrayList<>();
            List<GstCalculator.Line> lines = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                double qty = rnd.nextInt(4) == 0 ? (1 + rnd.nextInt(40)) / 4.0 : 1 + rnd.nextInt(12);
                double price = rnd.nextBoolean() ? 10 + rnd.nextInt(2000) : (1000 + rnd.nextInt(200_000)) / 100.0;
                String rate = rates[rnd.nextInt(rates.length)];
                boolean applicable = rate != null;
                Double ratePct = rate == null ? 0d : Double.valueOf(rate);
                double total = qty * price;
                legacyItems.add(new Legacy.Item(total, applicable, ratePct));
                lines.add(GstCalculator.Line.of(total, ratePct, applicable));
            }
            double discount = rnd.nextInt(5) == 0 ? rnd.nextInt(5000) / 100.0 : 0d;
            boolean interstate = rnd.nextInt(4) == 0;
            double tcs = rnd.nextInt(10) == 0 ? 0.1 : 0d;

            Legacy.Amounts old = Legacy.populate(legacyItems, discount, interstate, tcs);
            GstCalculator.Result now = GstCalculator.compute(lines,
                    discount == 0d ? null : BigDecimal.valueOf(discount), interstate,
                    tcs == 0d ? null : BigDecimal.valueOf(tcs));

            String ctx = "case " + n;
            // Untouched figure.
            assertEquals(old.discount, now.discount().toPlainString(), ctx + " discount");

            // Rule 2 at line level: the subtotal is the sum of the lines AS PRINTED (each to paise). A
            // fractional quantity can make a line ₹3.085, printed ₹3.09; the old subtotal summed the
            // unprinted value. At most half a paisa per line from the old.
            BigDecimal printedLines = BigDecimal.ZERO;
            for (GstCalculator.Line l : lines) printedLines = printedLines.add(GstCalculator.paise(l.value()));
            assertEquals(printedLines, now.itemsSubtotal(), ctx);
            assertTrue(bd(old.subtotal).subtract(now.itemsSubtotal()).abs()
                    .compareTo(new BigDecimal("0.01").multiply(BigDecimal.valueOf(count))) <= 0, ctx + " subtotal");
            if (!old.subtotal.equals(now.itemsSubtotal().toPlainString())) subtotalChanged++;

            if (interstate) {
                assertTrue(bd(old.igst).subtract(now.igst()).abs().compareTo(bd("0.01")) <= 0, ctx + " igst");
                if (!old.igst.equals(now.igst().toPlainString())) igstChanged++;
            }

            // Rule 1: the halves are parts of the total, within a paisa of each other and adding up.
            assertTrue(now.cgst().subtract(now.sgst()).abs().compareTo(bd("0.01")) <= 0, ctx);
            assertEquals(now.cgst().add(now.sgst()).add(now.igst()), now.gstTotal(), ctx);
            assertTrue(bd(old.cgst).subtract(now.cgst()).abs().compareTo(bd("0.01")) <= 0, ctx + " cgst");
            if (!old.cgst.equals(now.cgst().toPlainString())) halvesChanged++;

            // Rule 2: the grand total is the sum of its parts. It may differ from the old by what the
            // printed lines moved the subtotal (carried through at most 28% tax), plus a paisa per
            // rounded head and one for TCS — and by nothing else.
            BigDecimal parts = now.itemsSubtotal().subtract(now.discount()).add(now.gstTotal())
                    .add(now.tcsAmount() == null ? BigDecimal.ZERO : now.tcsAmount());
            assertEquals(parts, now.grandTotal(), ctx);
            BigDecimal subtotalShift = bd(old.subtotal).subtract(now.itemsSubtotal()).abs();
            BigDecimal allowed = subtotalShift.multiply(bd("1.29")).add(bd("0.03"));
            assertTrue(bd(old.grandTotal).subtract(now.grandTotal()).abs().compareTo(allowed) <= 0,
                    ctx + " grand " + old.grandTotal + " vs " + now.grandTotal() + " (allowed " + allowed + ")");
            if (!old.grandTotal.equals(now.grandTotal().toPlainString())) totalChanged++;

            // The amount payable: only a grand total sitting on a half-rupee may round the other way.
            // The old code skipped round-off within half a paisa of a whole rupee (asking ₹14390.01 for
            // ₹14390.0099); a payable that moves by no more than the grand total did is that, not a
            // different rupee.
            String oldPayable = old.rounded != null ? old.rounded : old.grandTotal;
            BigDecimal payableShift = bd(oldPayable).subtract(now.payable()).abs();
            if (payableShift.signum() != 0 && payableShift.compareTo(allowed) <= 0) {
                payablePaiseOnly++;
            } else if (payableShift.signum() != 0) {
                BigDecimal frac = now.grandTotal().remainder(BigDecimal.ONE).abs();
                assertTrue(frac.subtract(bd("0.50")).abs().compareTo(allowed) <= 0,
                        ctx + " payable moved off a half-rupee: " + oldPayable + " -> " + now.payable());
                payableChanged++;
            }
        }
        // Printed so the size of each change is on record, not just its permission.
        System.out.printf("GstCalculator vs legacy over 20000 documents: subtotal %d, igst %d, halves %d, "
                        + "grand total %d corrected; amount payable moved by paise %d, by a rupee %d%n",
                subtotalChanged, igstChanged, halvesChanged, totalChanged, payablePaiseOnly, payableChanged);
        // Measured 2026-11-02: 105 of 20,000 (0.5%), every one a grand total within a few paise of a
        // half-rupee. That is acceptable where the document DEFINES the amount and not where money was
        // already charged — which is exactly why OMS keeps D2C, subscription and top-up documents on
        // the old arithmetic until checkout uses this class. The ceiling catches drift, not intent.
        assertTrue(payableChanged <= 150, "the amount payable moved on " + payableChanged + " documents");
    }

    /** Frozen copy of OMS {@code InvoiceBuilderService.populateAmounts} before 2026-11-02. Do not edit. */
    static final class Legacy {
        record Item(double total, boolean taxApplicable, double taxPercent) {}

        static final class Amounts {
            String subtotal, discount, cgst, sgst, igst, gst, tcs, grandTotal, rounding, rounded;
        }

        static Amounts populate(List<Item> items, double discount, boolean isInterstate, double tcsPercent) {
            Amounts amounts = new Amounts();
            double subtotal = 0d;
            double taxTotal = 0d;
            for (Item item : items) {
                subtotal += item.total();
                if (item.taxApplicable() && item.taxPercent() > 0)
                    taxTotal += item.total() * item.taxPercent() / 100.0;
            }
            amounts.subtotal = DecimalUtil.toStringTwoDecimalPlaces(subtotal);
            amounts.discount = DecimalUtil.toStringTwoDecimalPlaces(discount);
            if (isInterstate) {
                amounts.igst = DecimalUtil.toStringTwoDecimalPlaces(taxTotal);
                amounts.sgst = "0.00";
                amounts.cgst = "0.00";
            } else {
                amounts.sgst = DecimalUtil.toStringTwoDecimalPlaces(taxTotal / 2.0);
                amounts.cgst = DecimalUtil.toStringTwoDecimalPlaces(taxTotal / 2.0);
                amounts.igst = "0.00";
            }
            amounts.gst = DecimalUtil.toStringTwoDecimalPlaces(taxTotal);
            double preTcs = subtotal - discount + taxTotal;
            double tcsAmount = tcsPercent > 0 ? preTcs * tcsPercent / 100.0 : 0d;
            double grandTotal = preTcs + tcsAmount;
            if (tcsPercent > 0) amounts.tcs = DecimalUtil.toStringTwoDecimalPlaces(tcsAmount);
            amounts.grandTotal = DecimalUtil.toStringTwoDecimalPlaces(grandTotal);
            long rounded = Math.round(grandTotal);
            double rounding = rounded - grandTotal;
            if (Math.abs(rounding) >= 0.005) {
                amounts.rounding = (rounding > 0 ? "+" : "") + DecimalUtil.toStringTwoDecimalPlaces(rounding);
                amounts.rounded = DecimalUtil.toStringTwoDecimalPlaces(rounded * 1d);
            }
            return amounts;
        }
    }
}
