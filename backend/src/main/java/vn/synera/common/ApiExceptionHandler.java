package vn.synera.common;

import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.dao.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(BusinessException.class)
  ResponseEntity<?> business(BusinessException e) {
    return ResponseEntity.status(e.status).body(ApiResponse.fail(e.code, e.getMessage(), Map.of()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException e) {
    Map<String, Object> fields = new LinkedHashMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(
            x -> fields.put(x.getField(), Objects.toString(x.getDefaultMessage(), "Không hợp lệ")));
    return ResponseEntity.badRequest()
        .body(ApiResponse.fail("VALIDATION_FAILED", "Dữ liệu nhập không hợp lệ.", fields));
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.bind.MissingRequestHeaderException.class,
    HandlerMethodValidationException.class
  })
  ResponseEntity<?> malformed(Exception e) {
    return ResponseEntity.badRequest()
        .body(
            ApiResponse.fail(
                "VALIDATION_FAILED",
                "Yêu cầu không đúng định dạng hoặc thiếu trường bắt buộc.",
                Map.of()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<?> denied() {
    return ResponseEntity.status(403)
        .body(ApiResponse.fail("FORBIDDEN_SCOPE", "Không có quyền thực hiện thao tác.", Map.of()));
  }

  @ExceptionHandler({OptimisticLockingFailureException.class, CannotAcquireLockException.class})
  ResponseEntity<?> concurrency(Exception e) {
    return ResponseEntity.status(409)
        .body(
            ApiResponse.fail(
                "OPTIMISTIC_LOCK",
                "Dữ liệu đang được xử lý đồng thời. Tải lại và thử lại.",
                Map.of()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> integrity() {
    return ResponseEntity.status(409)
        .body(
            ApiResponse.fail(
                "DUPLICATE_REQUEST",
                "Dữ liệu trùng hoặc không thỏa ràng buộc cơ sở dữ liệu.",
                Map.of()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e) {
    LoggerFactory.getLogger(getClass()).error("Unhandled request error", e);
    return ResponseEntity.status(500)
        .body(
            ApiResponse.fail(
                "INTERNAL_ERROR",
                "Không thể xử lý yêu cầu. Dùng X-Request-ID để tra cứu.",
                Map.of()));
  }
}
