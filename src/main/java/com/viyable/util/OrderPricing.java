package com.viyable.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.List;
import java.util.Map;

/**
 * What a D2C order is charged, and how that charge divides between the sellers who will each invoice
 * their part — ONE definition, used by checkout (ordering-service) to charge and by OMS to invoice.
 *
 * <p><b>⚠ WHY ONE DEFINITION (found 2026-11-02).</b> Checkout charged {@code subtotal − discount +
 * delivery + tax rounded to a whole rupee}, with the discount before tax and the tenant's fallback
 * rate. The invoice re-read each product's rate LIVE, taxed the undiscounted lines, and subtracted the
 * discount afterwards. The two agreed in prod only because no order yet had a discount. A document that
 * asks for a different amount than was charged is the one a customer argues with.
 *
 * <p><b>The rules.</b>
 * <ul>
 *   <li>Each seller's part is a document of its own: its items, its share of the order discount, its
 *       share of the delivery fee, priced by {@link GstCalculator#computeDiscountBeforeTax} and rounded
 *       as its invoice will be — by that seller's {@link AmountRounding}.</li>
 *   <li>The order is charged the SUM of the sellers' payables — never a total rounded separately,
 *       which could differ from the invoices by a rupee for every seller.</li>
 *   <li>Discount and delivery are shared out in proportion to each seller's items, at paise, the last
 *       seller taking the remainder — so the shares add up exactly.</li>
 *   <li>Delivery is never discounted. How it is taxed is the {@link DeliveryTaxRule} the order was charged under:
 *       {@code UNTAXED} — a line of its own at 0% (every order before 2026-11-26); {@code INCLUSIVE_COMPOSITE} — the
 *       fee is GST-inclusive, shared across the seller's items by value, each share carved into taxable value + GST at
 *       its item's rate (§17.13.11). The customer pays the fee either way; only paise of rounding can move.</li>
 * </ul>
 *
 * <p>Sellers are taken in order of their key, never in the order lines arrive: checkout lists them as
 * the cart does and OMS as it saved them, and a split that depended on either would put a paisa of
 * discount on a different seller's invoice than the one it was charged to.
 */
public final class OrderPricing {

    private OrderPricing() {}

    /** One order line. {@code ratePercent} null or 0 = not taxed; {@code sellerKey} = whoever invoices it. */
    public record Item(String sellerKey, BigDecimal unitPrice, BigDecimal quantity, BigDecimal ratePercent) {
        public BigDecimal value() {
            return GstCalculator.paise(unitPrice.multiply(quantity));
        }
    }

    /**
     * One item's part of a seller's GST-inclusive delivery fee ({@link DeliveryTaxRule#INCLUSIVE_COMPOSITE}).
     *
     * @param itemIndex   the item's position in the list the seller's part was priced from
     * @param share       the fee shared to it, GST included
     * @param taxable     the taxable value carved out of {@code share} at {@code ratePercent} — what the invoice prints
     * @param ratePercent the item's rate; null = the share is not taxed and {@code taxable} equals it
     */
    public record DeliveryShare(int itemIndex, BigDecimal share, BigDecimal taxable, BigDecimal ratePercent) {}

    /**
     * One seller's part — exactly what their invoice will say.
     *
     * @param deliveryFee    the seller's share of the fee as the customer pays it (GST included under the composite rule)
     * @param deliveryShares per item under {@link DeliveryTaxRule#INCLUSIVE_COMPOSITE}; empty under UNTAXED or with no fee
     */
    public record SellerPart(String sellerKey, BigDecimal discount, BigDecimal deliveryFee,
                             GstCalculator.Result amounts, DeliveryTaxRule deliveryTaxRule,
                             List<DeliveryShare> deliveryShares) {

        /** What the delivery adds to the document's subtotal: the fee itself when untaxed, its taxable value otherwise. */
        public BigDecimal deliveryTaxable() {
            if (deliveryTaxRule != DeliveryTaxRule.INCLUSIVE_COMPOSITE || deliveryShares.isEmpty()) return deliveryFee;
            BigDecimal t = BigDecimal.ZERO.setScale(2);
            for (DeliveryShare d : deliveryShares) t = t.add(d.taxable());
            return t;
        }
    }

    public record Priced(List<SellerPart> sellers, BigDecimal itemsSubtotal, BigDecimal discount,
                         BigDecimal deliveryFee, BigDecimal gstTotal, BigDecimal payable) {

        public SellerPart seller(String key) {
            for (SellerPart p : sellers) if (p.sellerKey().equals(key)) return p;
            return null;
        }

        /**
         * The GST to show BESIDE a delivery fee shown as the customer pays it: every GST on the order except what is
         * already inside a GST-inclusive fee. So {@code itemsSubtotal − discount + deliveryFee + gstBesideFee}, plus the
         * round-off, is {@code payable}, and a screen listing those rows adds up (owner, 2026-11-26: the customer sees
         * "Delivery ₹20" as quoted; the invoice prints the legal split). Equals {@link #gstTotal} under UNTAXED.
         */
        public BigDecimal gstBesideFee() {
            BigDecimal inside = BigDecimal.ZERO;
            for (SellerPart p : sellers) inside = inside.add(p.deliveryFee().subtract(p.deliveryTaxable()));
            return gstTotal.subtract(inside);
        }
    }

    public static Priced price(List<Item> items, BigDecimal discount, BigDecimal deliveryFee) {
        return price(items, discount, deliveryFee, seller -> AmountRounding.NEAREST_RUPEE);
    }

    /**
     * The order, each seller's part rounded by THAT seller's setting — every seller invoices their own
     * part, so it is their rounding the customer's charge has to match.
     */
    public static Priced price(List<Item> items, BigDecimal discount, BigDecimal deliveryFee,
                               java.util.function.Function<String, AmountRounding> roundingOfSeller) {
        return price(items, discount, deliveryFee, roundingOfSeller, DeliveryTaxRule.UNTAXED);
    }

    /** The same, the delivery fee taxed by {@code rule} — the rule the order is charged, and later invoiced, under. */
    public static Priced price(List<Item> items, BigDecimal discount, BigDecimal deliveryFee,
                               java.util.function.Function<String, AmountRounding> roundingOfSeller,
                               DeliveryTaxRule rule) {
        Map<String, List<Item>> bySeller = new TreeMap<>();
        for (Item item : items) {
            bySeller.computeIfAbsent(item.sellerKey(), k -> new ArrayList<>()).add(item);
        }
        List<String> keys = new ArrayList<>(bySeller.keySet());
        List<BigDecimal> sellerValues = new ArrayList<>();
        for (String key : keys) {
            BigDecimal v = BigDecimal.ZERO;
            for (Item item : bySeller.get(key)) v = v.add(item.value());
            sellerValues.add(v);
        }

        List<BigDecimal> discounts = share(GstCalculator.paise(discount), sellerValues);
        List<BigDecimal> deliveries = share(GstCalculator.paise(deliveryFee), sellerValues);

        List<SellerPart> parts = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO, gst = BigDecimal.ZERO, payable = BigDecimal.ZERO;
        BigDecimal disc = BigDecimal.ZERO, delivery = BigDecimal.ZERO;
        for (int i = 0; i < keys.size(); i++) {
            SellerPart part = priceSeller(keys.get(i), bySeller.get(keys.get(i)), discounts.get(i), deliveries.get(i),
                    false, null, roundingOfSeller.apply(keys.get(i)), rule);
            parts.add(part);
            subtotal = subtotal.add(part.amounts().itemsSubtotal()).subtract(part.deliveryTaxable());
            gst = gst.add(part.amounts().gstTotal());
            payable = payable.add(part.amounts().payable());
            disc = disc.add(part.discount());
            delivery = delivery.add(part.deliveryFee());
        }
        return new Priced(parts, subtotal, disc, delivery, gst, payable);
    }

    /**
     * One seller's document, given its share of discount and delivery. OMS calls this directly to
     * invoice a fulfilment group with the shares it stored, so the invoice and the charge cannot drift.
     */
    public static SellerPart priceSeller(String sellerKey, List<Item> items, BigDecimal discount, BigDecimal deliveryFee) {
        return priceSeller(sellerKey, items, discount, deliveryFee, false, null, AmountRounding.NEAREST_RUPEE);
    }

    /**
     * The same, labelled for the invoice: {@code interstate} decides IGST versus CGST + SGST (never the
     * amount — GstCalculator rule 1), and {@code tcsPercent} adds TCS where the firm collects it.
     * ⚠ TCS is the one figure checkout does not charge; pass null unless the document must carry it.
     */
    public static SellerPart priceSeller(String sellerKey, List<Item> items, BigDecimal discount, BigDecimal deliveryFee,
                                         boolean interstate, BigDecimal tcsPercent) {
        return priceSeller(sellerKey, items, discount, deliveryFee, interstate, tcsPercent, AmountRounding.NEAREST_RUPEE);
    }

    /**
     * The same, rounded as {@code rounding} says — for an order, the rounding stamped on it when it
     * was charged, never the firm's current setting.
     */
    public static SellerPart priceSeller(String sellerKey, List<Item> items, BigDecimal discount, BigDecimal deliveryFee,
                                         boolean interstate, BigDecimal tcsPercent, AmountRounding rounding) {
        return priceSeller(sellerKey, items, discount, deliveryFee, interstate, tcsPercent, rounding, DeliveryTaxRule.UNTAXED);
    }

    /**
     * The same, the delivery fee taxed by {@code rule} — for a stored group, the rule stamped on it when it was charged.
     * Under {@link DeliveryTaxRule#INCLUSIVE_COMPOSITE} the fee becomes one non-discountable line per item share (see
     * {@link #deliveryShares}). GST is still computed once over the whole document, so how a document later GROUPS
     * those lines for printing cannot move a paisa of it.
     */
    public static SellerPart priceSeller(String sellerKey, List<Item> items, BigDecimal discount, BigDecimal deliveryFee,
                                         boolean interstate, BigDecimal tcsPercent, AmountRounding rounding,
                                         DeliveryTaxRule rule) {
        DeliveryTaxRule taxRule = java.util.Objects.requireNonNull(rule, "deliveryTaxRule");
        List<GstCalculator.Line> lines = new ArrayList<>();
        for (Item item : items) lines.add(new GstCalculator.Line(item.value(), item.ratePercent(), true));
        BigDecimal delivery = GstCalculator.paise(deliveryFee);
        List<DeliveryShare> shares = List.of();
        if (delivery.signum() != 0) {
            if (taxRule == DeliveryTaxRule.INCLUSIVE_COMPOSITE && !items.isEmpty()) {
                shares = deliveryShares(items, delivery);
                for (DeliveryShare d : shares) lines.add(new GstCalculator.Line(d.taxable(), d.ratePercent(), false));
            } else {
                lines.add(new GstCalculator.Line(delivery, null, false));
            }
        }
        GstCalculator.Result r = GstCalculator.computeDiscountBeforeTax(lines, discount, interstate, tcsPercent, rounding);
        return new SellerPart(sellerKey, GstCalculator.paise(discount), delivery, r, taxRule, shares);
    }

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * A seller's GST-inclusive fee shared across its items by value, at paise, the last share taking the remainder;
     * each share carved into taxable value at its item's rate ({@code share × 100 / (100 + rate)}, to paise).
     *
     * <p>⚠ "Last" is in a FIXED order — by rate, then value — never the order the lines arrive in: checkout lists them
     * as the cart does and OMS as it saved them, and a remainder that followed either could put a paisa at a different
     * rate on the invoice than it was charged at. Items equal in rate and value are interchangeable for the arithmetic.
     * When the items are worth nothing in all (a free sample alone), the last takes the whole fee, so no fee is lost.
     * Zero shares are left out. The result is in item order.
     */
    static List<DeliveryShare> deliveryShares(List<Item> items, BigDecimal fee) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) order.add(i);
        order.sort((a, b) -> {
            int c = rateOf(items.get(a)).compareTo(rateOf(items.get(b)));
            return c != 0 ? c : items.get(a).value().compareTo(items.get(b).value());
        });
        List<BigDecimal> weights = new ArrayList<>();
        for (int i : order) weights.add(items.get(i).value());
        List<BigDecimal> split = share(fee, weights);
        BigDecimal given = BigDecimal.ZERO;
        for (BigDecimal s : split) given = given.add(s);
        int last = split.size() - 1;
        if (given.compareTo(fee) != 0) split.set(last, split.get(last).add(fee.subtract(given)));

        List<DeliveryShare> out = new ArrayList<>();
        for (int k = 0; k < order.size(); k++) {
            BigDecimal s = split.get(k);
            if (s.signum() == 0) continue;
            Item item = items.get(order.get(k));
            BigDecimal rate = item.ratePercent() != null && item.ratePercent().signum() > 0 ? item.ratePercent() : null;
            BigDecimal taxable = rate == null ? s
                    : s.multiply(HUNDRED).divide(HUNDRED.add(rate), 2, RoundingMode.HALF_UP);
            out.add(new DeliveryShare(order.get(k), s, taxable, rate));
        }
        out.sort(java.util.Comparator.comparingInt(DeliveryShare::itemIndex));
        return out;
    }

    private static BigDecimal rateOf(Item item) {
        return item.ratePercent() == null || item.ratePercent().signum() < 0 ? BigDecimal.ZERO : item.ratePercent();
    }

    /** An amount shared out in proportion to the weights, at paise, the last share taking the remainder. */
    static List<BigDecimal> share(BigDecimal amount, List<BigDecimal> weights) {
        List<BigDecimal> out = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal w : weights) total = total.add(w);
        BigDecimal given = BigDecimal.ZERO;
        for (int i = 0; i < weights.size(); i++) {
            BigDecimal s;
            if (amount.signum() == 0 || total.signum() == 0) s = BigDecimal.ZERO.setScale(2);
            else if (i == weights.size() - 1) s = amount.subtract(given);
            else s = amount.multiply(weights.get(i)).divide(total, 2, RoundingMode.HALF_UP);
            out.add(s);
            given = given.add(s);
        }
        return out;
    }
}
