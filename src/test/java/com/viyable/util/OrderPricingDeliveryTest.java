package com.viyable.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Delivery as a composite supply (ACCOUNTING_PLAN.md §17.13.11): the fee is GST-inclusive, shared across the seller's
 * items by value, each share taxed at its item's rate — and UNTAXED, the rule every earlier order was charged under,
 * exactly as it always was.
 */
class OrderPricingDeliveryTest {

    private static final DeliveryTaxRule COMPOSITE = DeliveryTaxRule.INCLUSIVE_COMPOSITE;

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private static OrderPricing.Item item(String seller, String price, int qty, String rate) {
        return new OrderPricing.Item(seller, bd(price), BigDecimal.valueOf(qty), rate == null ? null : bd(rate));
    }

    private static OrderPricing.Priced composite(List<OrderPricing.Item> items, String discount, String fee) {
        return OrderPricing.price(items, discount == null ? null : bd(discount), bd(fee),
                s -> AmountRounding.NEAREST_RUPEE, COMPOSITE);
    }

    /** The owner's example: ₹20 beside 18% goods is ₹16.95 + ₹3.05, and the customer still pays ₹138. */
    @Test
    void aFeeBesideEighteenPercentGoodsCarriesEighteenPercentInsideIt() {
        OrderPricing.Priced p = composite(List.of(item("S", "100", 1, "18")), null, "20");
        OrderPricing.SellerPart s = p.sellers().get(0);
        assertEquals(1, s.deliveryShares().size());
        assertEquals(bd("20.00"), s.deliveryShares().get(0).share());
        assertEquals(bd("16.95"), s.deliveryShares().get(0).taxable());
        assertEquals(bd("16.95"), s.deliveryTaxable());
        assertEquals(bd("20.00"), s.deliveryFee(), "the fee as the customer pays it");
        assertEquals(bd("116.95"), s.amounts().itemsSubtotal(), "the invoice's subtotal carries the taxable part");
        assertEquals(bd("21.05"), p.gstTotal());
        assertEquals(bd("138.00"), p.payable());
        assertEquals(bd("100.00"), p.itemsSubtotal(), "goods only");
        assertEquals(bd("18.00"), p.gstBesideFee(), "the GST shown beside 'Delivery ₹20'");
    }

    /** Nil-rated goods make a nil-rated delivery: no GST anywhere, the fee whole. */
    @Test
    void aFeeBesideNilRatedGoodsIsNilRated() {
        OrderPricing.Priced p = composite(List.of(item("S", "150", 2, "0")), null, "30");
        OrderPricing.SellerPart s = p.sellers().get(0);
        assertEquals(bd("30.00"), s.deliveryTaxable());
        assertNull(s.deliveryShares().get(0).ratePercent());
        assertEquals(bd("0.00"), p.gstTotal());
        assertEquals(bd("330.00"), p.payable());
    }

    /** A mixed order: the fee follows the goods' values, each share at its own item's rate. */
    @Test
    void aMixedOrderSharesTheFeeByValueEachShareAtItsItemsRate() {
        OrderPricing.Priced p = composite(List.of(item("S", "100", 1, "18"), item("S", "100", 1, "0")), null, "20");
        OrderPricing.SellerPart s = p.sellers().get(0);
        assertEquals(2, s.deliveryShares().size());
        OrderPricing.DeliveryShare taxed = s.deliveryShares().get(0), nil = s.deliveryShares().get(1);
        assertEquals(0, taxed.itemIndex());
        assertEquals(bd("10.00"), taxed.share());
        assertEquals(bd("8.47"), taxed.taxable());
        assertEquals(bd("10.00"), nil.taxable());
        // (100 + 8.47) × 18% = 19.5246 → 19.52; 200 + 18.47 + 19.52 = 237.99 → ₹238, what the old rule charged too
        assertEquals(bd("19.52"), p.gstTotal());
        assertEquals(bd("238.00"), p.payable());
        assertEquals(bd("200.00"), p.itemsSubtotal());
    }

    /** IGST or CGST + SGST is a label: the GST total, and so the charge, does not depend on it. */
    @Test
    void interstateChangesTheHeadsNotTheTotal() {
        List<OrderPricing.Item> items = List.of(item("S", "333.33", 3, "12"), item("S", "45", 1, "5"));
        OrderPricing.SellerPart in = OrderPricing.priceSeller("S", items, bd("10"), bd("40"), false, null,
                AmountRounding.NEAREST_RUPEE, COMPOSITE);
        OrderPricing.SellerPart out = OrderPricing.priceSeller("S", items, bd("10"), bd("40"), true, null,
                AmountRounding.NEAREST_RUPEE, COMPOSITE);
        assertEquals(in.amounts().gstTotal(), out.amounts().gstTotal());
        assertEquals(in.amounts().payable(), out.amounts().payable());
        assertEquals(out.amounts().gstTotal(), out.amounts().igst());
        assertEquals(in.amounts().gstTotal(), in.amounts().cgst().add(in.amounts().sgst()));
    }

    /** The order discount is spread over the goods only; the fee is not discounted under either rule. */
    @Test
    void theFeeIsNeverDiscounted() {
        List<OrderPricing.Item> items = List.of(item("S", "500", 1, "18"));
        OrderPricing.Priced p = composite(items, "100", "20");
        // goods taxed on 400 → 72; fee 16.95 + 3.051 → GST (400 + 16.95) × 18% = 75.051 → 75.05
        assertEquals(bd("75.05"), p.gstTotal());
        assertEquals(bd("492.00"), p.payable(), "500 − 100 + 16.95 + 75.05 = 492.00");
        assertEquals(bd("72.00"), p.gstBesideFee());
    }

    /** A waived fee is ₹0: nothing to share, nothing to tax — the same document either rule would make. */
    @Test
    void aZeroFeeIsTheSameUnderBothRules() {
        List<OrderPricing.Item> items = List.of(item("S", "199.50", 2, "5"), item("S", "60", 1, "0"));
        OrderPricing.SellerPart c = OrderPricing.priceSeller("S", items, bd("15"), BigDecimal.ZERO, false, null,
                AmountRounding.NEAREST_RUPEE, COMPOSITE);
        OrderPricing.SellerPart u = OrderPricing.priceSeller("S", items, bd("15"), BigDecimal.ZERO);
        assertEquals(u.amounts(), c.amounts());
        assertTrue(c.deliveryShares().isEmpty());
    }

    /** Checkout lists lines as the cart does and OMS as it saved them: the composite split must not care. */
    @Test
    void theSharesDoNotDependOnTheOrderOfTheLines() {
        Random rnd = new Random(20261126L);
        String[] rates = {null, "0", "5", "12", "18"};
        for (int n = 0; n < 2_000; n++) {
            List<OrderPricing.Item> items = randomItems(rnd, rates, "S");
            List<OrderPricing.Item> shuffled = new ArrayList<>(items);
            Collections.shuffle(shuffled, rnd);
            BigDecimal fee = BigDecimal.valueOf(1 + rnd.nextInt(9_999), 2).add(BigDecimal.valueOf(rnd.nextInt(80)));
            OrderPricing.SellerPart a = OrderPricing.priceSeller("S", items, null, fee, false, null, AmountRounding.NONE, COMPOSITE);
            OrderPricing.SellerPart b = OrderPricing.priceSeller("S", shuffled, null, fee, false, null, AmountRounding.NONE, COMPOSITE);
            assertEquals(a.amounts(), b.amounts(), "case " + n);
        }
    }

    /**
     * The invariants, over carts shaped like real ones: the shares add up to the fee to the paisa; each share's taxable
     * value is its share carved at its rate; the screen's rows (goods − discount + fee + GST beside it) plus the
     * round-off are the charge; and the charge moves from the UNTAXED one by less than a rupee only where GST is now
     * inside the fee — never by more than the fee's own paise of rounding when every item is untaxed.
     */
    @Test
    void theSharesAddUpAndTheScreenRowsAddUpToTheCharge() {
        Random rnd = new Random(17131L);
        String[] rates = {null, "0", "5", "12", "18", "28"};
        for (int n = 0; n < 20_000; n++) {
            List<OrderPricing.Item> items = new ArrayList<>(randomItems(rnd, rates, "A"));
            if (rnd.nextBoolean()) items.addAll(randomItems(rnd, rates, "B"));
            BigDecimal fee = rnd.nextInt(5) == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(10 + rnd.nextInt(90));
            BigDecimal discount = rnd.nextInt(3) == 0 ? BigDecimal.valueOf(rnd.nextInt(150)) : null;
            AmountRounding rounding = rnd.nextBoolean() ? AmountRounding.NEAREST_RUPEE : AmountRounding.NONE;
            OrderPricing.Priced p = OrderPricing.price(items, discount, fee, s -> rounding, COMPOSITE);

            BigDecimal shared = BigDecimal.ZERO, roundOff = BigDecimal.ZERO;
            for (OrderPricing.SellerPart s : p.sellers()) {
                BigDecimal sellerShares = BigDecimal.ZERO;
                for (OrderPricing.DeliveryShare d : s.deliveryShares()) {
                    sellerShares = sellerShares.add(d.share());
                    BigDecimal expect = d.ratePercent() == null ? d.share()
                            : d.share().multiply(bd("100")).divide(bd("100").add(d.ratePercent()), 2, RoundingMode.HALF_UP);
                    assertEquals(expect, d.taxable(), "case " + n);
                }
                assertEquals(s.deliveryFee(), s.deliveryShares().isEmpty() ? s.deliveryFee() : sellerShares, "case " + n);
                shared = shared.add(s.deliveryFee());
                if (s.amounts().roundOff() != null) roundOff = roundOff.add(s.amounts().roundOff());
            }
            assertEquals(GstCalculator.paise(fee), shared, "case " + n + ": the fee is shared out exactly");
            BigDecimal rows = p.itemsSubtotal().subtract(p.discount()).add(p.deliveryFee()).add(p.gstBesideFee()).add(roundOff);
            assertEquals(p.payable(), rows, "case " + n + ": the screen's rows add up to the charge");
        }
    }

    /**
     * ⚠ UNTAXED MUST BE BYTE-IDENTICAL TO WHAT IT WAS. {@link Before} is a verbatim copy of {@code priceSeller} before
     * the rule existed; every old group's invoice replays through it, so not a paisa may move.
     */
    @Test
    void untaxedIsExactlyTheOldArithmetic() {
        Random rnd = new Random(1102L);
        String[] rates = {null, "0", "5", "12", "18"};
        for (int n = 0; n < 20_000; n++) {
            List<OrderPricing.Item> items = randomItems(rnd, rates, "S");
            BigDecimal fee = rnd.nextBoolean() ? BigDecimal.ZERO : BigDecimal.valueOf(rnd.nextInt(9_000), 2);
            BigDecimal discount = rnd.nextInt(3) == 0 ? BigDecimal.valueOf(rnd.nextInt(15_000), 2) : null;
            boolean inter = rnd.nextBoolean();
            AmountRounding rounding = rnd.nextBoolean() ? AmountRounding.NEAREST_RUPEE : AmountRounding.NONE;
            GstCalculator.Result before = Before.priceSeller(items, discount, fee, inter, rounding);
            OrderPricing.SellerPart now = OrderPricing.priceSeller("S", items, discount, fee, inter, null, rounding,
                    DeliveryTaxRule.UNTAXED);
            assertEquals(before, now.amounts(), "case " + n);
            assertEquals(GstCalculator.paise(fee), now.deliveryTaxable());
            assertTrue(now.deliveryShares().isEmpty());
        }
        // and the old overloads are UNTAXED
        List<OrderPricing.Item> items = List.of(item("A", "100", 1, "18"), item("B", "50", 2, "5"));
        OrderPricing.Priced old = OrderPricing.price(items, bd("10"), bd("49"));
        OrderPricing.Priced untaxed = OrderPricing.price(items, bd("10"), bd("49"), s -> AmountRounding.NEAREST_RUPEE,
                DeliveryTaxRule.UNTAXED);
        assertEquals(old, untaxed);
        assertEquals(old.gstTotal(), old.gstBesideFee());
    }

    /**
     * A seller whose items are worth nothing in all (a free sample alone) still carries its fee share — it is never lost
     * between its items. (Between SELLERS an order worth ₹0 shares out no fee at all; that split is untouched by the rule.)
     */
    @Test
    void itemsWorthNothingStillCarryTheFee() {
        OrderPricing.SellerPart s = OrderPricing.priceSeller("S", List.of(item("S", "0", 1, "18"), item("S", "0", 2, "5")),
                null, bd("25"), false, null, AmountRounding.NEAREST_RUPEE, COMPOSITE);
        BigDecimal total = BigDecimal.ZERO;
        for (OrderPricing.DeliveryShare d : s.deliveryShares()) total = total.add(d.share());
        assertEquals(bd("25.00"), total);
        assertEquals(bd("25.00"), s.amounts().payable());
    }

    /** A misspelt rule is refused, never read as UNTAXED; absent means a sender that predates the rule. */
    @Test
    void anUnknownRuleIsRefused() {
        assertEquals(DeliveryTaxRule.UNTAXED, DeliveryTaxRule.of(null));
        assertEquals(DeliveryTaxRule.UNTAXED, DeliveryTaxRule.of(" "));
        assertEquals(COMPOSITE, DeliveryTaxRule.of("INCLUSIVE_COMPOSITE"));
        assertThrows(IllegalArgumentException.class, () -> DeliveryTaxRule.of("COMPOSITE"));
    }

    private static List<OrderPricing.Item> randomItems(Random rnd, String[] rates, String seller) {
        int count = 1 + rnd.nextInt(5);
        List<OrderPricing.Item> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int qty = 1 + rnd.nextInt(6);
            BigDecimal price = rnd.nextBoolean() ? BigDecimal.valueOf(10 + rnd.nextInt(900))
                    : BigDecimal.valueOf(500 + rnd.nextInt(90_000), 2);
            String rate = rates[rnd.nextInt(rates.length)];
            items.add(new OrderPricing.Item(seller, price, BigDecimal.valueOf(qty), rate == null ? null : bd(rate)));
        }
        return items;
    }

    /** Frozen copy of {@code OrderPricing.priceSeller} as of 2026-11-25 (before {@link DeliveryTaxRule}). */
    static final class Before {
        static GstCalculator.Result priceSeller(List<OrderPricing.Item> items, BigDecimal discount, BigDecimal deliveryFee,
                                                boolean interstate, AmountRounding rounding) {
            List<GstCalculator.Line> lines = new ArrayList<>();
            for (OrderPricing.Item item : items) lines.add(new GstCalculator.Line(item.value(), item.ratePercent(), true));
            BigDecimal delivery = GstCalculator.paise(deliveryFee);
            if (delivery.signum() != 0) lines.add(new GstCalculator.Line(delivery, null, false));
            return GstCalculator.computeDiscountBeforeTax(lines, discount, interstate, null, rounding);
        }
    }
}
