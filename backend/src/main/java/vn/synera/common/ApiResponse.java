package vn.synera.common;

import java.util.Map;

public record ApiResponse<T>(boolean success, T data, ApiError error) {
  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, null);
  }

  public static ApiResponse<Void> fail(String code, String message, Map<String, Object> details) {
    return new ApiResponse<>(false, null, new ApiError(code, message, details));
  }

  public record ApiError(String code, String message, Map<String, Object> details) {}
}
