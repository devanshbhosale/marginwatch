package com.devanshbhosale.marginwatch.dto;

/**
 * isDemo is true only for the public demo accounts.
 */
public record TokenRes(String token, UserRes user) {

    public record UserRes(String email, String fullName, String role, boolean isDemo) {
    }
}
