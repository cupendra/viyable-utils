package com.viyable.util;

import com.viyable.util.SeriesDecision.Continue;
import com.viyable.util.SeriesDecision.Open;
import com.viyable.util.SeriesDecision.Refused;
import com.viyable.util.SeriesDecision.Reopen;
import com.viyable.util.SeriesDecision.Series;
import com.viyable.util.SeriesDecision.Wanted;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What happens to a series when the next number is drawn (ACCOUNTING_PLAN.md §17.13.7–9). */
class SeriesDecisionTest {

    static final long MAX = 999_999;
    static final Series LEGACY_CN = new Series(1, "CN/GNTS/2026-27/", 76, MAX);
    static final Series TYPE_YEAR_CN = new Series(2, "CN/2627/", 3, MAX);

    @Test
    void theSameSettingsContinueTheLiveSeries() {
        assertEquals(new Continue(2, 4),
                SeriesDecision.decide(TYPE_YEAR_CN, List.of(LEGACY_CN), new Wanted("CN/2627/", MAX)));
    }

    /** The prod switch-over: a LEGACY series is live, the firm's settings call for the new format. */
    @Test
    void newSettingsCloseTheLiveSeriesAndOpenANewOneAtOne() {
        Open open = assertInstanceOf(Open.class,
                SeriesDecision.decide(LEGACY_CN, List.of(), new Wanted("CN/2627/", MAX)));
        assertEquals(new Open("CN/2627/", MAX, 1L), open);
        assertEquals(1, open.serial());
    }

    /** ⚠ Going back to a prefix already used this year continues it: never a second series with the same numbers. */
    @Test
    void goingBackToAPrefixUsedThisYearReopensItAndContinues() {
        Series compact = new Series(3, "CN2627", 9, MAX);
        assertEquals(new Reopen(2, 4, 3L),
                SeriesDecision.decide(compact, List.of(LEGACY_CN, TYPE_YEAR_CN), new Wanted("CN/2627/", MAX)));
    }

    @Test
    void aTypesFirstDocumentOfTheYearOpensItsSeries() {
        assertEquals(new Open("CN/2728/", MAX, null), SeriesDecision.decide(null, List.of(), new Wanted("CN/2728/", MAX)));
        assertEquals(new Open("CN/GNTS/", MAX, null), SeriesDecision.decide(null, null, new Wanted("CN/GNTS/", MAX)));
    }

    /** ⚠ Unreadable settings never become a guessed format: the live series continues, and with none, nothing issues. */
    @Test
    void unreadableSettingsContinueTheLiveSeriesOrRefuse() {
        assertEquals(new Continue(2, 4), SeriesDecision.decide(TYPE_YEAR_CN, List.of(), null));
        Refused refused = assertInstanceOf(Refused.class, SeriesDecision.decide(null, List.of(TYPE_YEAR_CN), null));
        assertTrue(refused.sentence().contains("could not be read"), refused.sentence());
    }

    /** ⚠ A full series refuses in words naming the fix — never a number cut short or widened. */
    @Test
    void aFullSeriesRefusesAndSaysWhatToDo() {
        Series full = new Series(4, "CN/2627/", MAX, MAX);
        Refused live = assertInstanceOf(Refused.class, SeriesDecision.decide(full, List.of(), new Wanted("CN/2627/", MAX)));
        assertEquals("Series CN/2627/ has used all 999,999 of its numbers for this year. Nothing was issued. Change your"
                + " code in the document-number settings to start a new series.", live.sentence());
        assertInstanceOf(Refused.class, SeriesDecision.decide(null, List.of(full), new Wanted("CN/2627/", MAX)));
        assertInstanceOf(Refused.class, SeriesDecision.decide(full, List.of(), null));
        assertInstanceOf(Refused.class, SeriesDecision.decide(null, List.of(), new Wanted("X/", 0)));
    }

    /** Said in words once a tenth is left: for six digits, from the 900,000th document. */
    @Test
    void runningLowIsSaidFromTheLastTenth() {
        assertNull(SeriesDecision.runningLow(new Series(1, "CN/2627/", 899_999, MAX)));
        assertEquals("Series CN/2627/ has 99,999 numbers left this year, of 999,999. When they are used up nothing more"
                        + " can be issued in it; changing your code in the document-number settings starts a new series.",
                SeriesDecision.runningLow(new Series(1, "CN/2627/", 900_000, MAX)));
        assertNull(SeriesDecision.runningLow(null));
    }
}
