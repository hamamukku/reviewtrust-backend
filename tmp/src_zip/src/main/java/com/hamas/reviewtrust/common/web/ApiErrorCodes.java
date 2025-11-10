package com.hamas.reviewtrust.common.web;

/**
 * Unified API error codes for both public and administrative endpoints.
 *
 * <p>The API specification describes error responses as:
 * {@code { "error": { "code", "message", "hint" } }}, where
 * {@code code} corresponds to one of the values defined here. A
 * frontend or middleware can map these codes to localized messages
 * or user‑friendly descriptions. Additional codes should be added
 * here as new failure scenarios are introduced.</p>
 */
public enum ApiErrorCodes {
    /** Validation failed due to incorrect input or missing required data */
    E_VALIDATION,
    /** Resource not found */
    E_NOT_FOUND,
    /** Authentication failed or token missing */
    E_UNAUTHORIZED,
    /** Insufficient permissions for the requested operation */
    E_FORBIDDEN,
    /** Conflict occurred (duplicate resource or state mismatch) */
    E_CONFLICT,
    /** Scraping job timed out */
    E_SCRAPE_TIMEOUT,
    /** Generic scraping failure */
    E_SCRAPE_FAILED,
    /** Scoring engine failure */
    E_SCORING_FAILED,
    /** Unhandled server error */
    E_INTERNAL;
}