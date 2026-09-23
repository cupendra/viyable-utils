package com.viyable.util;

public class TimeZoneConstants {

	/**
	 * <b>⚠⚠ A REGION ID, NEVER THE THREE-LETTER ABBREVIATION.</b> This held {@code "IST"} until
	 * 2026-09-23, and the two readers disagree about whether that is a timezone:
	 *
	 * <ul>
	 * <li>{@code TimeZone.getTimeZone("IST")} resolves it, through a legacy short-id table — which
	 * is why every {@code @Scheduled(zone = ...)} in firmland fired at exactly the right minute;</li>
	 * <li>{@code ZoneId.of("IST")} <b>throws</b> {@code Unknown time-zone ID: IST}, because
	 * {@code java.time} deliberately dropped the ambiguous abbreviations.</li>
	 * </ul>
	 *
	 * <p><b>⚠ So the cron fired and the work threw</b> — and the run log filled with tidy rows
	 * saying the job had run, 0 records, because the exception was caught and recorded as a remark
	 * nobody reads. {@code PAY_REVISION_TASK} and {@code SETTLEMENT_TASK} had never once done any
	 * work; a revision approved for the 1st simply never came into force.
	 *
	 * <p>⚠ Also genuinely ambiguous: IST is Indian, Irish and Israel Standard Time depending on who
	 * is reading. The region id says which.
	 */
	public static final String INDIAN_STANDARD_TIME = "Asia/Kolkata";

}
