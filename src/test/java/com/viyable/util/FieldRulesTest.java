package com.viyable.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The one definition of what a typed field may hold.
 *
 * <p>⚠ The proof that the BROWSER twin still agrees with these lives beside that file, in firmland's
 * {@code FieldRulesJsTest}: it runs {@code field-rules.js} under node and compares its answers with these, case by
 * case. A sentence changed here and not there is caught by that test, not by this one.
 */
class FieldRulesTest {

    /** ⚠ Blank is never a rule's business: required-ness is asked separately, and most fields are optional. */
    @Test
    void nothingTypedIsNothingToJudge() {
        assertNull(FieldRules.phoneProblem(null));
        assertNull(FieldRules.phoneProblem("  "));
        assertNull(FieldRules.emailProblem(""));
        assertNull(FieldRules.nameProblem(null, "a name"));
        assertNull(FieldRules.lengthProblem(null, 10, "The notes"));
        assertNull(FieldRules.moneyProblem(null, "The salary"));
    }

    /**
     * <b>⚠⚠ TEN DIGITS, AND NOTHING BUT DIGITS</b> (owner, 2026-09-18). The punctuation forms are refused rather than
     * quietly stripped: accepting "+91 99029 00119" and storing "9902900119" means the same number is held two ways
     * across the product, matching neither a search nor another record.
     */
    @Test
    void aphoneNumberIsTenBareDigits() {
        assertNull(FieldRules.phoneProblem("9902900119"));
        assertNull(FieldRules.phoneProblem("  9902900119  "), "trimmed before judging");

        assertEquals("A phone number is 10 digits.", FieldRules.phoneProblem("99029001"));
        assertEquals("A phone number is 10 digits.", FieldRules.phoneProblem("6754328123123123131321"));
        assertEquals("A phone number is digits only, with no spaces or symbols.",
                FieldRules.phoneProblem("+91 99029 00119"));
        assertEquals("A phone number is digits only, with no spaces or symbols.",
                FieldRules.phoneProblem("(990) 290-0119"));
    }

    @Test
    void anemailLooksLikeOneOrIsRefused() {
        assertNull(FieldRules.emailProblem("raghu@example.com"));
        assertNull(FieldRules.emailProblem("first.last+tag@sub.example.co.in"));
        assertEquals("That email address does not look right.",
                FieldRules.emailProblem("12309183012313123131!@#!@#!@#!@#"));
        assertEquals("That email address does not look right.", FieldRules.emailProblem("asha@example"));
        // ⚠ Owner's screenshot, 2026-09-18: this reached the form and was accepted by the looser pattern.
        assertEquals("That email address does not look right.", FieldRules.emailProblem("Ra@ra.c!@#!@#"));
    }

    /**
     * <b>⚠⚠ A NAME IS LETTERS AND THE PUNCTUATION NAMES CARRY</b> — not "contains a letter somewhere", which let
     * "Upendr a@!@3123123^&%&%&#$%" through the offer form (owner, 2026-09-18).
     */
    @Test
    void anameIsLettersInAnyScriptWithTheirPunctuation() {
        assertNull(FieldRules.nameProblem("Asha Rao", "a name"));
        assertNull(FieldRules.nameProblem("ಅಶಾ", "a name"), "any script — a name need not be English");
        assertNull(FieldRules.nameProblem("Dr. Rao", "a name"));
        assertNull(FieldRules.nameProblem("O'Brien", "a name"));
        assertNull(FieldRules.nameProblem("Jean-Luc", "a name"));

        assertEquals("That does not look like a name.", FieldRules.nameProblem("12345", "a name"));
        assertEquals("That does not look like a name.",
                FieldRules.nameProblem("Upendr a@!@3123123^&%&%&#$%", "a name"));
        assertEquals("That does not look like a name.", FieldRules.nameProblem("Asha 3", "a name"),
                "digits belong in no name");
        assertEquals("That does not look like a name.", FieldRules.nameProblem("'Asha", "a name"),
                "and it starts with a letter");
    }

    /** Ordinary typed text: wider than a name, still not anything. */
    @Test
    void atextFieldTakesThePunctuationItsContentCarries() {
        assertNull(FieldRules.textProblem("Sr. Engineer (Backend)", "The designation"));
        assertNull(FieldRules.textProblem("Hyderabad - Unit 2", "The work location"));
        assertNull(FieldRules.textProblem("Sales & Marketing", "The designation"));

        assertEquals("The designation can use letters, numbers and . , ' & ( ) / - only.",
                FieldRules.textProblem("Engineer @#$%^", "The designation"));
    }

    /** ⚠ The limit AND the length: "too long" about a 1,000-character note is not actionable. */
    @Test
    void alengthProblemSaysBothNumbers() {
        assertNull(FieldRules.lengthProblem("N".repeat(10), 10, "The notes"));
        assertEquals("The notes — the limit is 10 characters; this is 11.",
                FieldRules.lengthProblem("N".repeat(11), 10, "The notes"));
    }

    /**
     * <b>⚠⚠ THE PRECISION IS PART OF THE RULE.</b> A money figure lands in DECIMAL(15,2): more than thirteen whole
     * digits or more than two decimals is a database error at save time — a 500 naming no field — instead of a
     * sentence somebody can act on.
     */
    @Test
    void amoneyFigureFitsItsColumn() {
        assertNull(FieldRules.moneyProblem(new BigDecimal("40000.50"), "The salary"));
        assertNull(FieldRules.moneyProblem(new BigDecimal("9999999999999.99"), "The salary"), "the largest that fits");
        assertEquals("The salary cannot be negative.", FieldRules.moneyProblem(new BigDecimal("-1"), "The salary"));
        assertEquals("The salary has more than 2 decimal places.",
                FieldRules.moneyProblem(new BigDecimal("40000.123"), "The salary"));
        assertEquals("The salary is too large.",
                FieldRules.moneyProblem(new BigDecimal("10000000000000"), "The salary"));
    }

    @Test
    void collectKeepsOnlyTheProblemsThatExist() {
        List<String> problems = new ArrayList<>();
        FieldRules.collect(problems, null, "A problem.", null, "Another.");
        assertEquals(List.of("A problem.", "Another."), problems);
    }
}
