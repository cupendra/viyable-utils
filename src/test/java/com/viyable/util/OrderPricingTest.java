package com.viyable.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderPricingTest {

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private static OrderPricing.Item item(String seller, String price, int qty, String rate) {
        return new OrderPricing.Item(seller, bd(price), BigDecimal.valueOf(qty), rate == null ? null : bd(rate));
    }

    /** The prod shape: one seller, no discount. ₹585 at 5% + no delivery = ₹614.25 → charged ₹614. */
    @Test
    void oneSellerIsChargedWhatItsInvoiceWillSay() {
        OrderPricing.Priced p = OrderPricing.price(List.of(item("900004", "585", 1, "5")), null, BigDecimal.ZERO);
        assertEquals(bd("614.00"), p.payable());
        assertEquals(bd("29.25"), p.gstTotal());
        assertEquals(p.payable(), p.sellers().get(0).amounts().payable());
    }

    /** The order's charge is the sum of the sellers' invoices — each rounded as its document will be. */
    @Test
    void severalSellersAreChargedTheSumOfTheirInvoices() {
        OrderPricing.Priced p = OrderPricing.price(List.of(
                item("A", "199.50", 1, "5"), item("B", "333.33", 2, "18"), item("A", "45.25", 3, "12")),
                bd("50"), bd("49"));

        BigDecimal sum = BigDecimal.ZERO, disc = BigDecimal.ZERO, delivery = BigDecimal.ZERO;
        for (OrderPricing.SellerPart s : p.sellers()) {
            sum = sum.add(s.amounts().payable());
            disc = disc.add(s.discount());
            delivery = delivery.add(s.deliveryFee());
        }
        assertEquals(sum, p.payable());
        assertEquals(bd("50.00"), disc, "the discount is shared out exactly");
        assertEquals(bd("49.00"), delivery, "the delivery fee is shared out exactly");
        assertNotNull(p.seller("A"));
        assertEquals(2, p.sellers().size());
    }

    /** Checkout lists lines as the cart does, OMS as it saved them: the split must not care. */
    @Test
    void theSplitDoesNotDependOnTheOrderOfTheLines() {
        List<OrderPricing.Item> one = List.of(
                item("900004", "199.50", 1, "5"), item("900017", "333.33", 2, "18"), item("900004", "45.25", 3, "12"));
        List<OrderPricing.Item> other = List.of(one.get(1), one.get(2), one.get(0));
        OrderPricing.Priced a = OrderPricing.price(one, bd("50.01"), bd("49"));
        OrderPricing.Priced b = OrderPricing.price(other, bd("50.01"), bd("49"));
        assertEquals(a.seller("900004").discount(), b.seller("900004").discount());
        assertEquals(a.seller("900017").deliveryFee(), b.seller("900017").deliveryFee());
        assertEquals(a.payable(), b.payable());
    }

    /** OMS invoicing one seller from its stored shares lands on exactly that seller's part of the charge. */
    @Test
    void aSellerPricedAloneMatchesItsPartOfTheOrder() {
        List<OrderPricing.Item> a = List.of(item("A", "199.50", 1, "5"), item("A", "45.25", 3, "12"));
        List<OrderPricing.Item> all = new ArrayList<>(a);
        all.add(item("B", "333.33", 2, "18"));
        OrderPricing.Priced p = OrderPricing.price(all, bd("50"), bd("49"));
        OrderPricing.SellerPart fromOrder = p.seller("A");

        OrderPricing.SellerPart alone = OrderPricing.priceSeller("A", a, fromOrder.discount(), fromOrder.deliveryFee());
        assertEquals(fromOrder.amounts(), alone.amounts());
    }

    /**
     * ⚠ THE OWNER-APPROVED CHANGE, MEASURED (2026-11-02). {@link OldCheckout} is a verbatim copy of
     * ordering's {@code CheckoutService} arithmetic before this class replaced it. Across 20,000 carts
     * shaped like real ones, the amount charged may move by AT MOST ONE RUPEE — the old code rounded
     * the tax to a rupee and left the total with paise; the new one taxes to the paisa and rounds the
     * total. Anything larger means the new arithmetic changed something it was not meant to.
     */
    @Test
    void theChargeMovesByAtMostARupeeFromTheOldCheckout() {
        Random rnd = new Random(20261102L);
        String[] rates = {null, "0", "5", "12", "18"};
        int moved = 0, wholeRupeeCarts = 0, wholeRupeeMoved = 0;
        BigDecimal largest = BigDecimal.ZERO;
        for (int n = 0; n < 20_000; n++) {
            int count = 1 + rnd.nextInt(5);
            List<OrderPricing.Item> items = new ArrayList<>();
            List<double[]> old = new ArrayList<>();
            boolean wholeRupees = true;
            for (int i = 0; i < count; i++) {
                int qty = 1 + rnd.nextInt(6);
                double price = rnd.nextBoolean() ? 10 + rnd.nextInt(900) : (500 + rnd.nextInt(90_000)) / 100.0;
                String rate = rates[rnd.nextInt(rates.length)];
                if (price != Math.floor(price)) wholeRupees = false;
                items.add(new OrderPricing.Item("S", BigDecimal.valueOf(price), BigDecimal.valueOf(qty),
                        rate == null ? null : bd(rate)));
                old.add(new double[]{price, qty, rate == null ? 0 : Double.parseDouble(rate) / 100.0});
            }
            double discount = rnd.nextInt(4) == 0 ? rnd.nextInt(200) : 0;
            double delivery = rnd.nextBoolean() ? 49 : 0;

            double before = OldCheckout.total(old, discount, delivery);
            BigDecimal now = OrderPricing.price(items, BigDecimal.valueOf(discount), BigDecimal.valueOf(delivery)).payable();
            BigDecimal diff = now.subtract(BigDecimal.valueOf(before)).abs();
            assertTrue(diff.compareTo(bd("1.00")) <= 0, "case " + n + ": " + before + " -> " + now);
            if (diff.compareTo(bd("0.005")) > 0) moved++;
            if (wholeRupees) {
                wholeRupeeCarts++;
                if (diff.compareTo(bd("0.005")) > 0) wholeRupeeMoved++;
            }
            if (diff.compareTo(largest) > 0) largest = diff;
        }
        System.out.printf("OrderPricing vs old checkout over 20000 carts: charge moved on %d, largest move %s; "
                + "whole-rupee carts %d, moved %d%n", moved, largest, wholeRupeeCarts, wholeRupeeMoved);
        // Measured 2026-11-02: whole-rupee carts (every prod price) moved on 1 of 3,897 — a half-rupee
        // tie. Paise-priced carts move by design: their total is now rounded to a rupee, by ≤ ₹1.
        assertTrue(wholeRupeeMoved * 200 <= wholeRupeeCarts,
                "whole-rupee carts moved on " + wholeRupeeMoved + " of " + wholeRupeeCarts);
    }

    /** Frozen copy of ordering CheckoutService computeItemsTax + computeOrderBreakdown before 2026-11-02. */
    static final class OldCheckout {
        static double total(List<double[]> lines, double discountAmount, double delivery) {
            double subtotal = 0, preDiscountTax = 0;
            for (double[] l : lines) {
                double itemSubtotal = l[1] * l[0];
                subtotal += itemSubtotal;
                preDiscountTax += itemSubtotal * l[2];
            }
            double discountedSubtotal = subtotal - discountAmount;
            Long tax = Math.round(preDiscountTax * discountedSubtotal / subtotal);
            return discountedSubtotal + delivery + tax.doubleValue();
        }
    }
}
