package com.viyable.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GstStatesTest {

    /** D10: two spellings of one state are one state — the tax head must not flip on a name. */
    @Test
    void twoSpellingsOfOneStateAreIntraState() {
        assertFalse(GstStates.interstate(null, null, "Odisha", null, "Orissa"));
        assertFalse(GstStates.interstate(null, null, "Telangana", null, " telangana "));
        assertFalse(GstStates.interstate(null, null, "Jammu & Kashmir", null, "Jammu and Kashmir"));
    }

    @Test
    void differentStatesAreInterState() {
        assertTrue(GstStates.interstate(null, null, "Telangana", null, "Karnataka"));
        assertTrue(GstStates.interstate("36", null, null, "29", null));
    }

    /** The seller's GSTIN places it; the buyer is placed by where the goods go, never by their GSTIN. */
    @Test
    void theSellerIsPlacedByItsGstinWhenNoCodeIsStored() {
        assertFalse(GstStates.interstate(null, "36AABCU9603R1ZM", null, null, "Telangana"));
        assertTrue(GstStates.interstate(null, "36AABCU9603R1ZM", null, "27", null));
    }

    @Test
    void aCodeWinsOverAMisspeltName() {
        assertFalse(GstStates.interstate("36", null, "Telengana", "36", "Telangana"));
        assertFalse(GstStates.interstate("7", null, null, "07", null), "a code written as one digit is still Delhi");
    }

    @Test
    void anUnplaceableSideIsIntraStateAsBefore() {
        assertFalse(GstStates.interstate(null, null, null, null, "Karnataka"));
        assertNull(GstStates.codeOf("Atlantis"));
        assertEquals("21", GstStates.codeOf("ORISSA"));
    }
}
