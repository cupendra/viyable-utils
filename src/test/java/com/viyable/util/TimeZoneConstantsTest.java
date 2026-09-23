package com.viyable.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every timezone constant must be one BOTH clocks agree on.
 *
 * <p><b>⚠⚠ THE TWO READERS DISAGREE, AND THAT IS THE WHOLE TRAP.</b> {@code
 * TimeZone.getTimeZone("IST")} resolves through a legacy short-id table; {@code ZoneId.of("IST")}
 * throws. A constant that only the first one accepts is therefore a value that works everywhere it
 * is READ by Spring's scheduler and explodes everywhere it is read by {@code java.time} — which is
 * the worst possible split, because the cron still fires at exactly the right minute and only the
 * work inside it dies.
 *
 * <p>⚠ That is how {@code PAY_REVISION_TASK} and {@code SETTLEMENT_TASK} ran nightly for their
 * whole lives having never applied a revision or settled a soul: caught exception, tidy run-log row,
 * 0 records, remark nobody reads.
 *
 * <p>⚠ Driven by REFLECTION over the class rather than a list written out here, so the next
 * constant somebody adds is checked without anybody remembering to add it.
 */
class TimeZoneConstantsTest {

    @Test
    void everyConstantIsAZoneJavaTimeAccepts() throws Exception {
        int checked = 0;
        for (Field field : TimeZoneConstants.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                continue;
            }
            String value = (String) field.get(null);
            checked++;
            assertDoesNotThrow(() -> ZoneId.of(value),
                    field.getName() + " is \"" + value + "\", which java.time refuses."
                            + " Use the region id (Asia/Kolkata), never the abbreviation");
        }
        assertTrue(checked > 0, "reflection found no constants — this test would pass vacuously");
    }

    /**
     * ⚠ And the fix must not have moved anybody's clock. Asia/Kolkata IS what
     * {@code TimeZone.getTimeZone("IST")} was resolving to all along, so every {@code @Scheduled}
     * cron keeps firing at the minute it always did.
     */
    @Test
    void theindianZoneStillMeansWhatSpringWasAlreadyUsing() {
        assertEquals(TimeZone.getTimeZone("IST").getRawOffset(),
                TimeZone.getTimeZone(TimeZoneConstants.INDIAN_STANDARD_TIME).getRawOffset(),
                "changing the constant must not shift when any job runs");
    }
}
