package com.viyable.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How a firm rounds the amount a document asks for — a per-firm setting
 * ({@code org_invoice_config.amount_rounding}), applied ONCE, to the final amount, by
 * {@link GstCalculator}. Never to a line, a tax head or a delivery: those stay at paise, and the
 * round-off is printed on its own line so the figures above the total still add up.
 *
 * <ul>
 *   <li>{@link #NEAREST_RUPEE} — the default, and what every document did before the setting
 *       existed. CGST Act §170 permits it: tax and the amount payable may be rounded to the nearest
 *       rupee.</li>
 *   <li>{@link #NONE} — the amount is asked to the paisa.</li>
 * </ul>
 *
 * <p>⚠ An ORDER is priced at the rounding it was charged under, stamped on it at placement — never the
 * firm's current setting, which may have changed since. The setting decides new orders only.
 */
public enum AmountRounding {
    NEAREST_RUPEE,
    NONE;

    /** The final amount, rounded as this setting says. */
    public BigDecimal apply(BigDecimal amount) {
        return this == NONE ? amount : amount.setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * A stored or transmitted value. {@code null} is {@link #NEAREST_RUPEE}: a sender built before the
     * setting existed sends none, and everything priced before it existed was rounded to the rupee. An
     * unknown name is refused rather than guessed — rounding a document the wrong way is a wrong
     * amount on it.
     */
    public static AmountRounding of(String name) {
        if (name == null || name.isBlank()) return NEAREST_RUPEE;
        return valueOf(name.trim());
    }
}
