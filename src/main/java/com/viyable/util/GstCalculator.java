package com.viyable.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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
 *   <li><b>The GST total is computed once, at the full rate, and the heads are its parts.</b> Within
 *       a state it is split into CGST and SGST that add up to it exactly — when the total has an odd
 *       paisa, CGST carries it (₹14.63 + ₹14.62 = ₹29.25). Across states it is all IGST. So the total
 *       does not depend on where the buyer is, which is what lets checkout charge the right amount
 *       without knowing the seller's state, and a document reproduce that charge to the paisa.</li>
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
 * <p><b>Discount: two timings.</b> {@link #compute} subtracts it after tax, as the existing
 * self-priced documents do. {@link #computeDiscountBeforeTax} spreads it over the discountable lines
 * first and taxes what is left — GST is due on the discounted value (CGST Act §15(3)(a)) — and is
 * what a D2C order is charged and invoiced with ({@link OrderPricing}).
 */
public final class GstCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWO = new BigDecimal("2");

    private GstCalculator() {}

    /**
     * One line: its value in rupees, its GST rate in percent ({@code null} or 0 = not taxed), and
     * whether an order discount may be spread onto it (a delivery charge is not).
     */
    public record Line(BigDecimal value, BigDecimal ratePercent, boolean discountable) {
        public Line {
            if (value == null) value = BigDecimal.ZERO;
        }

        public Line(BigDecimal value, BigDecimal ratePercent) {
            this(value, ratePercent, true);
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
     * Discount subtracted AFTER tax — the existing self-priced documents' arithmetic.
     *
     * @param lines        lines; a line with no rate is part of the subtotal and carries no tax
     * @param discount     null = none
     * @param interstate   IGST when true, CGST + SGST otherwise (the total is the same either way)
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
        return finish(subtotal, discount == null ? zero() : paise(discount), taxBase, interstate, tcsPercent);
    }

    /**
     * Discount spread over the discountable lines in proportion to their value BEFORE tax, and tax
     * charged on what remains. The subtotal printed is still the undiscounted lines, with the
     * discount on its own line, so the document reads the way the customer saw the cart.
     */
    public static Result computeDiscountBeforeTax(List<Line> lines, BigDecimal discount, boolean interstate,
                                                  BigDecimal tcsPercent) {
        List<Line> safe = lines == null ? List.of() : lines;
        BigDecimal disc = discount == null ? zero() : paise(discount);
        List<BigDecimal> shares = spread(disc, safe);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxBase = BigDecimal.ZERO;
        for (int i = 0; i < safe.size(); i++) {
            Line line = safe.get(i);
            BigDecimal value = paise(line.value());
            subtotal = subtotal.add(value);
            if (line.taxed()) taxBase = taxBase.add(value.subtract(shares.get(i)).multiply(line.ratePercent()));
        }
        return finish(subtotal, disc, taxBase, interstate, tcsPercent);
    }

    private static Result finish(BigDecimal subtotal, BigDecimal disc, BigDecimal taxBase,
                                 boolean interstate, BigDecimal tcsPercent) {
        // Rule 1: the total at the full rate, once; the heads are its parts.
        BigDecimal gstTotal = taxBase.divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal cgst = zero(), sgst = zero(), igst = zero();
        if (interstate) {
            igst = gstTotal;
        } else {
            cgst = gstTotal.divide(TWO, 2, RoundingMode.HALF_UP);
            sgst = gstTotal.subtract(cgst);
        }

        BigDecimal preTcs = subtotal.subtract(disc).add(gstTotal);
        BigDecimal tcsPct = tcsPercent != null && tcsPercent.signum() > 0 ? tcsPercent : null;
        BigDecimal tcs = tcsPct == null ? zero()
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

    /**
     * An amount spread over the discountable lines in proportion to their value, each share at
     * paise, the last discountable line taking the remainder so the shares add up exactly.
     * Non-discountable lines get zero.
     */
    static List<BigDecimal> spread(BigDecimal amount, List<Line> lines) {
        List<BigDecimal> shares = new ArrayList<>();
        BigDecimal base = BigDecimal.ZERO;
        int last = -1;
        for (int i = 0; i < lines.size(); i++) {
            shares.add(zero());
            if (lines.get(i).discountable()) {
                base = base.add(paise(lines.get(i).value()));
                last = i;
            }
        }
        if (amount.signum() == 0 || base.signum() == 0 || last < 0) return shares;

        BigDecimal given = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).discountable()) continue;
            BigDecimal share = i == last ? amount.subtract(given)
                    : amount.multiply(paise(lines.get(i).value())).divide(base, 2, RoundingMode.HALF_UP);
            shares.set(i, share);
            given = given.add(share);
        }
        return shares;
    }

    /** A rupee amount to paise. */
    public static BigDecimal paise(BigDecimal v) {
        return v == null ? zero() : v.setScale(2, RoundingMode.HALF_UP);
    }

    /** A {@code double} amount to paise, through its decimal string — never its binary expansion. */
    public static BigDecimal money(Double v) {
        return v == null ? zero() : paise(BigDecimal.valueOf(v));
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }

    private static BigDecimal decimal(Double v) {
        return BigDecimal.valueOf(v);
    }
}
