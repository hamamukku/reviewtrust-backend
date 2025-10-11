// ApiErrorCodes.java (placeholder)
package com.hamas.reviewtrust.common.web;

/**
 * 公開/管理APIの統一エラーコード。
 * 仕様のエラーフォーマット { "error": { "code","message","hint" } } に対応。 
 * 例: E_SCRAPE_TIMEOUT, E_SCORING_FAILED など。
 */
public enum ApiErrorCodes {
    E_VALIDATION,
    E_NOT_FOUND,
    E_UNAUTHORIZED,
    E_FORBIDDEN,
    E_CONFLICT,
    E_SCRAPE_TIMEOUT,
    E_SCRAPE_FAILED,
    E_SCORING_FAILED,
    E_INTERNAL
}
