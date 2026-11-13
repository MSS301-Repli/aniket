package com.animallocket.common.web;

import com.animallocket.common.Headers;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * Lỗi trả về theo RFC 7807 ({@code application/problem+json}) kèm trường {@code code} ổn định cho client.
 * Không bao giờ trả stack trace hay thông điệp nội bộ.
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException e) {
        return problem(e.status(), e.code(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, msg.isEmpty() ? "Dữ liệu không hợp lệ" : msg);
    }

    /** Ràng buộc trên tham số của controller có {@code @Validated}. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraint(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, msg);
    }

    /** Ràng buộc trên tham số khi Spring MVC tự kiểm (controller không có {@code @Validated}). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleMethodValidation(HandlerMethodValidationException e) {
        String params = e.getAllValidationResults().stream()
                .map(r -> r.getMethodParameter().getParameterName())
                .collect(Collectors.joining(", "));
        return problem(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, "Tham số không hợp lệ: " + params);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return problem(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, "Tham số " + e.getName() + " sai định dạng");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParam(MissingServletRequestParameterException e) {
        return problem(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, "Thiếu tham số " + e.getParameterName());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingHeader(MissingRequestHeaderException e) {
        if (Headers.PUBLIC_ID.equalsIgnoreCase(e.getHeaderName())) {
            // Gateway chỉ gắn X-Public-Id khi người dùng đã có hồ sơ: thiếu nghĩa là chưa đăng ký.
            return problem(HttpStatus.FORBIDDEN, "PROFILE_REQUIRED", "Cần hoàn tất đăng ký hồ sơ trước");
        }
        return problem(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, "Thiếu header " + e.getHeaderName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException e) {
        return problem(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, "Nội dung request không hợp lệ");
    }

    /** Vi phạm ràng buộc DB (unique, FK), thường do hai request đua nhau: xung đột, không phải lỗi hệ thống. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException e) {
        log.info("Vi phạm ràng buộc dữ liệu: {}", e.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "CONFLICT", "Dữ liệu đã thay đổi hoặc bị trùng, vui lòng thử lại");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleOther(Exception e) throws Exception {
        if (e.getClass().getName().startsWith("org.springframework.security.")) {
            throw e; // để Spring Security trả 401/403 đúng chuẩn
        }
        if (e instanceof ErrorResponse er) {
            // Lỗi chuẩn của Spring MVC (404 không có route, 405, 406, 415...): giữ đúng mã HTTP.
            HttpStatusCode status = er.getStatusCode();
            HttpStatus resolved = HttpStatus.resolve(status.value());
            String code = resolved == null ? "HTTP_" + status.value() : resolved.name();
            return problem(status, code, er.getBody().getDetail());
        }
        log.error("Lỗi không lường trước", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Lỗi hệ thống");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatusCode status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        HttpStatus resolved = HttpStatus.resolve(status.value());
        if (resolved != null) {
            pd.setTitle(resolved.getReasonPhrase());
        }
        pd.setProperty("code", code);
        return ResponseEntity.status(status).body(pd);
    }
}
