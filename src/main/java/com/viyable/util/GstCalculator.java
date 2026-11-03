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
     * <p>{@code roundedGrandTotal} is the amount payable after the firm's {@link AmountRounding} and
     * {@code roundOff} the difference, both {@code null} when rounding changes nothing (the grand total
     * is already a whole rupee, or the firm does not round).
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
        return compute(lines, discount, interstate, tcsPercent, AmountRounding.NEAREST_RUPEE);
    }

    /** The same, with the firm's rounding applied to the final amount. */
    public static Result compute(List<Line> lines, BigDecimal discount, boolean interstate, BigDecimal tcsPercent,
                                 AmountRounding rounding) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxBase = BigDecimal.ZERO;   // Σ value × rate, unrounded, in rupee-percent
        if (lines != null) {
            for (Line line : lines) {
                BigDecimal value = paise(line.value());
                subtotal = subtotal.add(value);
                if (line.taxed()) taxBase = taxBase.add(value.multiply(line.ratePercent()));
            }
        }
        return finish(subtotal, discount == null ? zero() : paise(discount), taxBase, interstate, tcsPercent, rounding);
    }

    /**
     * Discount spread over the discountable lines in proportion to their value BEFORE tax, and tax
     * charged on what remains. The subtotal printed is still the undiscounted lines, with the
     * discount on its own line, so the document reads the way the customer saw the cart.
     */
    public static Result computeDiscountBeforeTax(List<Line> lines, BigDecimal discount, boolean interstate,
                                                  BigDecimal tcsPercent) {
        return computeDiscountBeforeTax(lines, discount, interstate, tcsPercent, AmountRounding.NEAREST_RUPEE);
    }

    /** The same, with the firm's rounding applied to the final amount. */
    public static Result computeDiscountBeforeTax(List<Line> lines, BigDecimal discount, boolean interstate,
                                                  BigDecimal tcsPercent, AmountRounding rounding) {
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
        return finish(subtotal, disc, taxBase, interstate, tcsPercent, rounding);
    }

    private static Result finish(BigDecimal subtotal, BigDecimal disc, BigDecimal taxBase,
                                 boolean interstate, BigDecimal tcsPercent, AmountRounding rounding) {
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

        // Rounding happens here and nowhere else: once, on the final amount.
        BigDecimal rounded = java.util.Objects.requireNonNull(rounding, "rounding").apply(grand);
        BigDecimal roundOff = rounded.subtract(grand);
        boolean hasRoundOff = roundOff.signum() != 0;

        return new Result(subtotal, disc, cgst, sgst, igst, gstTotal,
                tcsPct, tcsPct == null ? null : tcs,
                grand,
                hasRoundOff ? roundOff.setScale(2) : null,
                hasRoundOff ? rounded.setScale(2) : null);
    }

    /** One row of a document's HSN / SAC summary: every taxed line of one code at one rate, added up. */
    public record HsnRow(String hsn, BigDecimal ratePercent, BigDecimal taxable,
                         BigDecimal cgst, BigDecimal sgst, BigDecimal igst, BigDecimal tax) {}

    /**
     * The HSN / SAC summary of the document {@code r} was computed for: one row per (code, rate), in the
     * order the codes first appear.
     *
     * <p><b>⚠ A TOTAL AND ITS BREAKDOWN COME FROM THE SAME DATA (D17, found 2026-11-03).</b> The template
     * used to print a row per LINE, taxing each line's undiscounted value and halving it on its own, so on a
     * discounted invoice the summary did not add up to the GST the same page charged. Here each row's taxable
     * value is what the document taxed (after a before-tax discount, when the document was priced that way),
     * and the document's GST and CGST are SHARED across the rows (largest remainder, at paise), so the rows
     * add up to the totals printed beside them exactly.
     *
     * @param hsnCodes          one per line, parallel to {@code lines}; null or blank prints as "-"
     * @param discountBeforeTax true when {@code r} came from {@link #computeDiscountBeforeTax}
     */
    public static List<HsnRow> hsnSummary(List<Line> lines, List<String> hsnCodes, boolean discountBeforeTax,
                                          boolean interstate, Result r) {
        List<HsnRow> rows = new ArrayList<>();
        if (lines == null || lines.isEmpty() || r == null) return rows;
        List<BigDecimal> shares = discountBeforeTax ? spread(r.discount(), lines) : null;

        java.util.LinkedHashMap<String, BigDecimal[]> groups = new java.util.LinkedHashMap<>(); // taxable, exact tax
        java.util.Map<String, String[]> keys = new java.util.HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (!line.taxed()) continue;
            String code = hsnCodes != null && i < hsnCodes.size() ? hsnCodes.get(i) : null;
            String hsn = code != null && !code.isBlank() ? code.trim() : "-";
            BigDecimal rate = line.ratePercent().stripTrailingZeros();
            String key = hsn + "|" + rate.toPlainString();
            BigDecimal taxable = paise(line.value()).subtract(shares == null ? BigDecimal.ZERO : shares.get(i));
            BigDecimal[] g = groups.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            g[0] = g[0].add(taxable);
            g[1] = g[1].add(taxable.multiply(line.ratePercent()).divide(HUNDRED, 10, RoundingMode.HALF_UP));
            keys.put(key, new String[]{hsn, rate.toPlainString()});
        }
        if (groups.isEmpty()) return rows;

        List<String> order = new ArrayList<>(groups.keySet());
        List<BigDecimal> exact = new ArrayList<>();
        for (String k : order) exact.add(groups.get(k)[1]);
        List<BigDecimal> tax = allocate(r.gstTotal(), exact);

        List<BigDecimal> halves = new ArrayList<>();
        for (BigDecimal t : tax) halves.add(t.divide(TWO, 10, RoundingMode.HALF_UP));
        List<BigDecimal> cgst = interstate ? null : allocate(r.cgst(), halves);

        for (int i = 0; i < order.size(); i++) {
            String[] k = keys.get(order.get(i));
            BigDecimal t = tax.get(i);
            BigDecimal c = interstate ? zero() : cgst.get(i);
            BigDecimal sg = interstate ? zero() : t.subtract(c);
            rows.add(new HsnRow(k[0], new BigDecimal(k[1]), paise(groups.get(order.get(i))[0]),
                    c, sg, interstate ? t : zero(), t));
        }
        return rows;
    }

    /** One line of a document as the register and the books read it: what was taxed, and its share of each head. */
    public record LineRow(BigDecimal taxable, BigDecimal cgst, BigDecimal sgst, BigDecimal igst) {}

    /**
     * Each line of a document with the figures the document itself printed (A3): its taxable value (its value
     * less its share of the document discount, which GST deducts from the taxable value), and its share of the
     * document's CGST, SGST and IGST (largest remainder, at paise) — so the lines always add up to the totals on
     * the page, whichever arithmetic produced them.
     *
     * @param discount the document's discount (0 or null = none); spread over the discountable lines
     */
    public static List<LineRow> lineRows(List<Line> lines, BigDecimal discount,
                                         BigDecimal cgst, BigDecimal sgst, BigDecimal igst) {
        List<LineRow> rows = new ArrayList<>();
        if (lines == null || lines.isEmpty()) return rows;
        BigDecimal disc = discount == null ? zero() : paise(discount);
        List<BigDecimal> shares = spread(disc, lines);
        List<BigDecimal> exact = new ArrayList<>();
        List<BigDecimal> taxable = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            BigDecimal t = paise(lines.get(i).value()).subtract(shares.get(i));
            taxable.add(t);
            exact.add(lines.get(i).taxed()
                    ? t.multiply(lines.get(i).ratePercent()).divide(HUNDRED, 10, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
        }
        List<BigDecimal> c = allocate(paise(cgst), exact);
        List<BigDecimal> s = allocate(paise(sgst), exact);
        List<BigDecimal> g = allocate(paise(igst), exact);
        for (int i = 0; i < lines.size(); i++) rows.add(new LineRow(taxable.get(i), c.get(i), s.get(i), g.get(i)));
        return rows;
    }

    /**
     * {@code total} (at paise) shared in proportion to {@code exact}: each share floored to paise, and the
     * paise left over given to the largest remainders, so the shares add up to {@code total} exactly.
     */
    static List<BigDecimal> allocate(BigDecimal total, List<BigDecimal> exact) {
        List<BigDecimal> out = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal e : exact) sum = sum.add(e);
        BigDecimal given = BigDecimal.ZERO;
        List<BigDecimal> remainders = new ArrayList<>();
        for (BigDecimal e : exact) {
            BigDecimal ideal = sum.signum() == 0 ? BigDecimal.ZERO
                    : total.multiply(e).divide(sum, 10, RoundingMode.HALF_UP);
            BigDecimal floor = ideal.setScale(2, RoundingMode.FLOOR);
            out.add(floor);
            remainders.add(ideal.subtract(floor));
            given = given.add(floor);
        }
        int left = total.subtract(given).movePointRight(2).intValue();
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < out.size(); i++) idx.add(i);
        idx.sort((x, y) -> remainders.get(y).compareTo(remainders.get(x)));
        for (int j = 0; j < left && j < idx.size(); j++) {
            int i = idx.get(j);
            out.set(i, out.get(i).add(new BigDecimal("0.01")));
        }
        return out;
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
