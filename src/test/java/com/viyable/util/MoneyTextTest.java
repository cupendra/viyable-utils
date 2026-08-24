package com.viyable.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The grouping table, asserted directly.
 *
 * <p>This formatter decides how every money figure in every service reads, and the Indian pattern
 * is precisely the kind of thing that is wrong by one comma while looking entirely plausible:
 * {@code 3,52,551.21} and {@code 352,551.21} are both "a number with commas in it" until somebody
 * judges the scale of a business from one.
 */
class MoneyTextTest {

    @Test
    void rupeesGroupByLakh() {
        assertEquals("3,52,551.21", MoneyText.of(352551.21d, "INR"));
        assertEquals("1,00,000.00", MoneyText.of(100000d, "INR"));
        assertEquals("1,23,45,678.00", MoneyText.of(12345678d, "INR"), "crore groups by two as well");
        assertEquals("999.00", MoneyText.of(999d, "INR"), "below a thousand there is nothing to group");
        assertEquals("9,999.00", MoneyText.of(9999d, "INR"), "the first group is still three");
    }

    @Test
    void everythingElseGroupsByThree() {
        assertEquals("352,551.21", MoneyText.of(352551.21d, "USD"));
        assertEquals("352,551.21", MoneyText.of(352551.21d, "AED"));
    }

    /**
     * ⚠ An unknown currency must NOT inherit the Indian convention.
     *
     * <p>Printing somebody else's money the common way is merely conventional; printing it by lakh
     * asserts a locale it does not have.
     */
    @Test
    void anUnknownOrMissingCurrencyFallsBackToGroupingByThree() {
        assertEquals("352,551.21", MoneyText.of(352551.21d, null));
        assertEquals("352,551.21", MoneyText.of(352551.21d, ""));
        assertEquals("352,551.21", MoneyText.of(352551.21d, "ZZZ"));
    }

    @Test
    void theCurrencyCodeIsMatchedWithoutRegardToCase() {
        assertEquals("3,52,551.21", MoneyText.of(352551.21d, "inr"));
        assertEquals("3,52,551.21", MoneyText.of(352551.21d, " InR "));
    }

    /** Rounding is HALF_UP and stated, not whatever DecimalFormat defaults to (HALF_EVEN). */
    @Test
    void roundingIsHalfUpRatherThanBankers() {
        assertEquals("2.35", MoneyText.of(2.345d, "USD"));
        assertEquals("2.45", MoneyText.of(2.445d, "USD"));
    }

    /** A negative zero is arithmetically fine and reads as a defect. */
    @Test
    void negativeZeroReadsAsZero() {
        assertEquals("0.00", MoneyText.of(-0.0d, "INR"));
    }

    @Test
    void aMissingAmountReadsAsZeroRatherThanAsABlank() {
        assertEquals("0.00", MoneyText.of((Double) null, "INR"));
    }

    /** The symbol comes from the org's own setting, so it is never hard-coded by a caller. */
    @Test
    void theSymbolIsSuppliedRatherThanAssumed() {
        assertEquals("\u20B93,52,551.21", MoneyText.withSymbol(352551.21d, "INR", "\u20B9"));
        assertEquals("$352,551.21", MoneyText.withSymbol(352551.21d, "USD", "$"));
        assertEquals("352,551.21", MoneyText.withSymbol(352551.21d, "USD", null),
                "no symbol configured is a missing prefix, not a missing number");
    }

    /** The table itself, so a future currency is added by intent rather than by accident. */
    @Test
    void thePatternTableIsExplicit() {
        assertEquals(true, MoneyText.groupsByLakh("INR"));
        assertEquals(false, MoneyText.groupsByLakh("GBP"));
    }
}
