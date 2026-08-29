package com.devanshbhosale.marginwatch.domain;

/**
 * Diff outcome for one price-file row.
 * OK — cost change applied keeps margin at/above the policy floor.
 * BREACH — resulting margin below the floor, or new cost ≥ selling price (loss).
 * NEW_SKU — SKU absent from the catalog; never applicable (skip-only).
 * NOT_FOUND — row unresolvable (missing SKU, unparseable cost, wrong vendor).
 * DUPLICATE — later occurrence of a SKU already seen in the same file.
 */
public enum RowFlag {
    OK, BREACH, NEW_SKU, NOT_FOUND, DUPLICATE
}
