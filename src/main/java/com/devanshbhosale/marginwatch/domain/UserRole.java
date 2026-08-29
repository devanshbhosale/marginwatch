package com.devanshbhosale.marginwatch.domain;

/**
 * Internal tool — no self-registration. Accounts exist only via seeding:
 * ADMIN applies breach overrides, edits selling prices and policies;
 * ANALYST uploads files, maintains mappings and applies normal rows;
 * VIEWER is the read-only demo role (GET-only, enforced server side).
 */
public enum UserRole {
    ADMIN, ANALYST, VIEWER
}
