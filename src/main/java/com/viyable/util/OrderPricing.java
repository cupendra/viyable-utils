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
 *   <li>Delivery is a line of its own, untaxed and not discounted — the arithmetic both sides have
 *       always used.</li>
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

    /** One seller's part — exactly what their invoice will say. */
    public record SellerPart(String sellerKey, BigDecimal discount, BigDecimal deliveryFee,
                             GstCalculator.Result amounts) {}

    public record Priced(List<SellerPart> sellers, BigDecimal itemsSubtotal, BigDecimal discount,
                         BigDecimal deliveryFee, BigDecimal gstTotal, BigDecimal payable) {

        public SellerPart seller(String key) {
            for (SellerPart p : sellers) if (p.sellerKey().equals(key)) return p;
            return null;
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
                    false, null, roundingOfSeller.apply(keys.get(i)));
            parts.add(part);
            subtotal = subtotal.add(part.amounts().itemsSubtotal()).subtract(part.deliveryFee());
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
        List<GstCalculator.Line> lines = new ArrayList<>();
        for (Item item : items) lines.add(new GstCalculator.Line(item.value(), item.ratePercent(), true));
        BigDecimal delivery = GstCalculator.paise(deliveryFee);
        if (delivery.signum() != 0) lines.add(new GstCalculator.Line(delivery, null, false));
        GstCalculator.Result r = GstCalculator.computeDiscountBeforeTax(lines, discount, interstate, tcsPercent, rounding);
        return new SellerPart(sellerKey, GstCalculator.paise(discount), delivery, r);
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
