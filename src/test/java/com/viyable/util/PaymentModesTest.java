package com.viyable.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentModesTest {

    @Test
    void everyWordOnAScreenOrAnOldRecordHasItsCode() {
        assertEquals("CASH", PaymentModes.code("Cash"));
        assertEquals("BANK_TRANSFER", PaymentModes.code("Online Transfer"));
        assertEquals("BANK_TRANSFER", PaymentModes.code("BANK_TRANSFER"));
        assertEquals("BANK_TRANSFER", PaymentModes.code(" bank transfer "));
        assertEquals("CARD", PaymentModes.code("Card Payment"));
        assertEquals("CHEQUE", PaymentModes.code("Cheque"));
        assertEquals("DD", PaymentModes.code("Demand Draft"));
        assertEquals("UPI", PaymentModes.code("upi"));
    }

    @Test
    void anythingUnrecognisedIsOtherNeverARefusal() {
        assertEquals("OTHER", PaymentModes.code(null));
        assertEquals("OTHER", PaymentModes.code("barter"));
    }

    @Test
    void labelsReadBackForAScreen() {
        assertEquals("Online Transfer", PaymentModes.label("BANK_TRANSFER"));
        assertEquals("Others", PaymentModes.label("NOT_A_CODE"));
    }
}
