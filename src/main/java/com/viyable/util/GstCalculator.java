package com.viyable.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The money on a GST document — subtotal, tax by head, TCS, grand total and round-off — computed
 * ONCE, in exact decimal arithmetic, so every document and every screen that prints these figures
 * agrees with itself and with the others.
 *
 * <p><b>Why this exists.</b> OMS computed a document's amounts in {@code double} and rounded each
 * printed figure on its own. Two consequences reached real documents (found 2026-11-02, 5 of 50 in
 * prod): CGST and SGST were each {@code tax/2} rounded separately, so the halves did not add up to
 * the GST total (₹14.63 + ₹14.63 under a ₹29.25 total); and the grand total was summed from the
 * UNROUNDED tax and TCS, so the printed lines could differ from the printed total by a paisa.
 *
 * <p><b>⚠ TWO RULES, AND THEY ARE THE POINT.</b>
 * <ol>
 *   <li><b>Each tax head is its own levy.</b> CGST and SGST are each charged at half the rate on
 *       the same taxable value, so each is computed and rounded independently. They are therefore
 *       always equal, and the GST total is their sum — never a total split afterwards.</li>
 *   <li><b>A total is the sum of the figures printed above it.</b> Every component is rounded to
 *       paise first; the grand total adds the rounded components. A total that does not equal its
 *       visible parts is read as an arithmetic error in the product.</li>
 * </ol>
 *
 * <p>Line totals are taken to paise as given: a line's value is money, and the document prints it
 * to two places. {@code double} inputs are accepted for the existing callers and converted through
 * their decimal string, never through their binary expansion — {@code 3 × 12.1} is 36.30, not
 * 36.300000000000004.
 *
 * <p>Tax is computed at document level, per head. Discount is subtracted after tax, as the existing
 * documents do; moving it before tax (GST is due on the discounted value, CGST Act §15(3)(a)) is a
 * separate change made together with checkout, so the document and the amount charged move as one.
 */
public final class GstCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWO_HUNDRED = new BigDecimal("200");

    private GstCalculator() {}

    /** One taxable line: its value in rupees and its GST rate in percent ({@code null} or 0 = not taxed). */
    public record Line(BigDecimal value, BigDecimal ratePercent) {
        public Line {
            if (value == null) value = BigDecimal.ZERO;
        }

        /** For callers still holding doubles. A null or non-taxed rate means the line carries no GST. */
        public static Line of(Double value, Double ratePercent, boolean taxApplicable) {
            return new Line(money(value), taxApplicable && ratePercent != null ? decimal(ratePercent) : null);
        }

        boolean taxed() {
            return ratePercent != null && ratePercent.signum() > 0;
        }
    }

    /**
     * The document's figures, each already at paise.
     *
     * <p>{@code roundedGrandTotal} is the whole-rupee amount payable and {@code roundOff} the
     * difference, both {@code null} when the grand total is already a whole rupee.
     */
    public record Result(BigDecimal itemsSubtotal, BigDecimal discount,
                         BigDecimal cgst, BigDecimal sgst, BigDecimal igst, BigDecimal gstTotal,
                         BigDecimal tcsPercent, BigDecimal tcsAmount,
                         BigDecimal grandTotal, BigDecimal roundOff, BigDecimal roundedGrandTotal) {

        /** What the document asks the customer to pay. */
        public BigDecimal payable() {
            return roundedGrandTotal != null ? roundedGrandTotal : grandTotal;
        }
    }

    /**
     * @param lines        taxable lines; a line with no rate is part of the subtotal and carries no tax
     * @param discount     subtracted after tax (see the class note); null = none
     * @param interstate   IGST when true, CGST + SGST otherwise
     * @param tcsPercent   TCS on the tax-inclusive amount; null or 0 = none
     */
    public static Result compute(List<Line> lines, BigDecimal discount, boolean interstate, BigDecimal tcsPercent) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxBase = BigDecimal.ZERO;   // Σ value × rate, unrounded, in rupee-percent
        if (lines != null) {
            for (Line line : lines) {
                BigDecimal value = paise(line.value());
                subtotal = subtotal.add(value);
                if (line.taxed()) taxBase = taxBase.add(value.multiply(line.ratePercent()));
            }
        }

        BigDecimal cgst = BigDecimal.ZERO.setScale(2);
        BigDecimal sgst = BigDecimal.ZERO.setScale(2);
        BigDecimal igst = BigDecimal.ZERO.setScale(2);
        if (interstate) {
            igst = taxBase.divide(HUNDRED, 2, RoundingMode.HALF_UP);
        } else {
            // Rule 1: each head at half the rate, rounded on its own — so they are always equal.
            cgst = taxBase.divide(TWO_HUNDRED, 2, RoundingMode.HALF_UP);
            sgst = cgst;
        }
        BigDecimal gstTotal = cgst.add(sgst).add(igst);

        BigDecimal disc = discount == null ? BigDecimal.ZERO.setScale(2) : paise(discount);
        BigDecimal preTcs = subtotal.subtract(disc).add(gstTotal);

        BigDecimal tcsPct = tcsPercent != null && tcsPercent.signum() > 0 ? tcsPercent : null;
        BigDecimal tcs = tcsPct == null ? BigDecimal.ZERO.setScale(2)
                : preTcs.multiply(tcsPct).divide(HUNDRED, 2, RoundingMode.HALF_UP);

        // Rule 2: the grand total is the sum of the rounded figures printed above it.
        BigDecimal grand = preTcs.add(tcs);

        BigDecimal rounded = grand.setScale(0, RoundingMode.HALF_UP);
        BigDecimal roundOff = rounded.subtract(grand);
        boolean hasRoundOff = roundOff.signum() != 0;

        return new Result(subtotal, disc, cgst, sgst, igst, gstTotal,
                tcsPct, tcsPct == null ? null : tcs,
                grand,
                hasRoundOff ? roundOff.setScale(2) : null,
                hasRoundOff ? rounded.setScale(2) : null);
    }

    /** A rupee amount to paise. */
    public static BigDecimal paise(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2) : v.setScale(2, RoundingMode.HALF_UP);
    }

    /** A {@code double} amount to paise, through its decimal string — never its binary expansion. */
    public static BigDecimal money(Double v) {
        return v == null ? BigDecimal.ZERO.setScale(2) : paise(BigDecimal.valueOf(v));
    }

    private static BigDecimal decimal(Double v) {
        return BigDecimal.valueOf(v);
    }
}
