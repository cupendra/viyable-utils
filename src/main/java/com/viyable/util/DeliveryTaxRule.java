package com.viyable.util;

/**
 * How an order's delivery fee is taxed — stamped on each seller's part when the order is charged, and replayed by
 * that part's invoice, so the document always repeats the arithmetic the customer paid under
 * (ACCOUNTING_PLAN.md §17.13.11).
 *
 * <ul>
 *   <li>{@link #UNTAXED} — the fee is a line of its own at 0%, not discounted. Every order charged before
 *       2026-11-26 was priced this way, and its invoice keeps it.</li>
 *   <li>{@link #INCLUSIVE_COMPOSITE} — a firm delivering its own goods makes ONE composite supply (CGST Act s.2(30)),
 *       taxed at the principal supply's rate (s.8(a)). The fee is what the customer pays, GST INCLUDED: it is shared
 *       across the seller's items by value and each share is carved into taxable value + GST at that item's rate and
 *       treatment (₹20 beside 18% goods = ₹16.95 + ₹3.05; beside nil-rated goods = ₹20 nil-rated). Not discounted.</li>
 * </ul>
 */
public enum DeliveryTaxRule {
    UNTAXED,
    INCLUSIVE_COMPOSITE;

    /**
     * A sent or stored name back to the rule. Null or blank is {@link #UNTAXED} — what a sender that predates the rule
     * charged. ⚠ Any other unknown name THROWS: a misspelt rule silently read as UNTAXED would invoice a fee the customer
     * paid GST-inclusive as a 0% line, and nothing would say so.
     */
    public static DeliveryTaxRule of(String name) {
        if (name == null || name.isBlank()) return UNTAXED;
        return valueOf(name.trim());
    }
}
