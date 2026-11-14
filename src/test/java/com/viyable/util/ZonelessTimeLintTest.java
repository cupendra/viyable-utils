package com.viyable.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ratchet itself: what it counts, and each way it refuses. */
class ZonelessTimeLintTest {

    @TempDir Path root;

    private void file(String rel, String body) throws Exception {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, body);
    }

    @Test
    void itCountsTheServersClockAndZone_notAZonedNow_notComments() throws Exception {
        file("a/Bad.java", """
                class Bad {
                    LocalDate d = LocalDate.now();
                    YearMonth m = YearMonth.now( );
                    ZoneId z = ZoneId.systemDefault();
                    Calendar c = Calendar.getInstance();
                    // LocalDate.now() in a comment is not code
                    LocalDate ok = LocalDate.now(zone);
                    LocalDate alsoOk = LocalDate.now(clock);
                }""");
        file("a/Good.java", "class Good { LocalDate d = FirmTime.today(zone, clock); }");

        assertEquals(Map.of("a/Bad.java", 4), ZonelessTimeLint.count(root));
    }

    @Test
    void aNewFileANewUseAndAFreedUseAreEachRefused_anExactListPasses() throws Exception {
        file("Old.java", "class Old { Object a = LocalDate.now(); Object b = LocalDate.now(); }");
        file("New.java", "class New { Object a = ZoneId.systemDefault(); }");
        Map<String, Integer> found = ZonelessTimeLint.count(root);

        List<String> p = ZonelessTimeLint.problems(found, Map.of("Old.java", 1, "Gone.java", 3));
        assertTrue(p.stream().anyMatch(s -> s.startsWith("New.java uses the server's clock")), p.toString());
        assertTrue(p.stream().anyMatch(s -> s.startsWith("Old.java now has 2")), p.toString());
        assertTrue(p.stream().anyMatch(s -> s.startsWith("Gone.java has none left")), p.toString());
        assertTrue(ZonelessTimeLint.problems(found, Map.of("Old.java", 3, "New.java", 1)).stream()
                .anyMatch(s -> s.startsWith("Old.java is down to 2 from 3")));
        assertTrue(ZonelessTimeLint.problems(found, Map.of("Old.java", 2, "New.java", 1)).isEmpty());
    }

    @Test
    void theAllowedListParsesAndTheAssertionFailsLoudly() throws Exception {
        file("X.java", "class X { Object a = LocalDateTime.now(); }");
        assertEquals(Map.of("X.java", 1), ZonelessTimeLint.parseAllowed(List.of("# header", "", "X.java=1")));
        ZonelessTimeLint.assertNoNew(root, List.of("X.java=1"));
        assertThrows(AssertionError.class, () -> ZonelessTimeLint.assertNoNew(root, List.of()));
    }
}
