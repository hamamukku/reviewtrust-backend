// GlobalExceptionHandler.java (placeholder)
package com.hamas.reviewtrust.api.advice;

import com.hamas.reviewtrust.common.web.ApiErrorCodes;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * APIの例外を { "error": { "code","message","hint" } } 形式で返す。
 * 仕様書のエラーフォーマットに準拠。 
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 400 Bad Request: Bean Validation（@Valid 等）でのメソッド引数エラー
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY) // 422
                .body(ErrorBody.of(ApiErrorCodes.E_VALIDATION.name(), msg, "入力内容を見直してください。"));
    }

    // 400 Bad Request: パラメータに対する ConstraintViolation
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorBody> handleConstraintViolation(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse("Invalid request");
        return ResponseEntity
                .badRequest()
                .body(ErrorBody.of(ApiErrorCodes.E_VALIDATION.name(), msg, "パラメータを修正してください。"));
    }

    // 任意の ResponseStatusException（404/403/401/他）
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorBody> handleResponseStatus(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        HttpStatus http = Optional.ofNullable(HttpStatus.resolve(status)).orElse(HttpStatus.BAD_REQUEST);
        String code = switch (status) {
            case 401 -> ApiErrorCodes.E_UNAUTHORIZED.name();
            case 403 -> ApiErrorCodes.E_FORBIDDEN.name();
            case 404 -> ApiErrorCodes.E_NOT_FOUND.name();
            case 409 -> ApiErrorCodes.E_CONFLICT.name();
            case 422 -> ApiErrorCodes.E_VALIDATION.name();
            default -> (status >= 400 && status < 500)
                    ? ApiErrorCodes.E_VALIDATION.name()
                    : ApiErrorCodes.E_INTERNAL.name();
        };
        String message = Optional.ofNullable(ex.getReason()).orElse(http.getReasonPhrase());
        return ResponseEntity.status(http).body(ErrorBody.of(code, message, null));
    }

    // 予期しない例外は 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleAny(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorBody.of(ApiErrorCodes.E_INTERNAL.name(),
                        "Unexpected error occurred.", "管理者に連絡してください。"));
    }

    /** { "error": { "code","message","hint" } } を表すシリアライズ用構造 */
    public static class ErrorBody {
        public Error error;

        public ErrorBody() { }

        private ErrorBody(String code, String message, String hint) {
            this.error = new Error(code, message, hint);
        }

        public static ErrorBody of(String code, String message, String hint) {
            return new ErrorBody(code, message, hint);
        }

        public static class Error {
            public String code;
            public String message;
            public String hint;

            public Error() { }

            public Error(String code, String message, String hint) {
                this.code = code;
                this.message = message;
                this.hint = hint;
            }
        }
    }
}
