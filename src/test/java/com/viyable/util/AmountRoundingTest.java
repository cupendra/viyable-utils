package com.viyable.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AmountRoundingTest {

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    /** ₹585 at 5% = ₹614.25: rounded, ₹614 with −0.25 printed; not rounded, ₹614.25 and no round-off line. */
    @Test
    void aFirmThatDoesNotRoundIsAskedThePaisa() {
        List<GstCalculator.Line> lines = List.of(new GstCalculator.Line(bd("585.00"), bd("5")));

        GstCalculator.Result rounded = GstCalculator.compute(lines, null, false, null, AmountRounding.NEAREST_RUPEE);
        assertEquals(bd("614.00"), rounded.payable());
        assertEquals(bd("-0.25"), rounded.roundOff());

        GstCalculator.Result exact = GstCalculator.compute(lines, null, false, null, AmountRounding.NONE);
        assertEquals(bd("614.25"), exact.payable());
        assertNull(exact.roundOff());
        assertNull(exact.roundedGrandTotal());
        assertEquals(rounded.gstTotal(), exact.gstTotal(), "rounding never touches the tax");
    }

    /** Each seller's part is rounded by THAT seller's setting, and the charge is still their sum. */
    @Test
    void eachSellerIsRoundedByItsOwnSetting() {
        List<OrderPricing.Item> items = List.of(
                new OrderPricing.Item("A", bd("585"), BigDecimal.ONE, bd("5")),
                new OrderPricing.Item("B", bd("585"), BigDecimal.ONE, bd("5")));
        OrderPricing.Priced p = OrderPricing.price(items, null, null,
                seller -> seller.equals("A") ? AmountRounding.NEAREST_RUPEE : AmountRounding.NONE);

        assertEquals(bd("614.00"), p.seller("A").amounts().payable());
        assertEquals(bd("614.25"), p.seller("B").amounts().payable());
        assertEquals(bd("1228.25"), p.payable());
    }

    @Test
    void noValueIsTheRupeeAndAnUnknownOneIsRefused() {
        assertEquals(AmountRounding.NEAREST_RUPEE, AmountRounding.of(null));
        assertEquals(AmountRounding.NONE, AmountRounding.of("NONE"));
        assertThrows(IllegalArgumentException.class, () -> AmountRounding.of("UP"));
    }
}
