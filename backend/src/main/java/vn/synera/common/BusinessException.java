package vn.synera.common;

public class BusinessException extends RuntimeException {
  public final String code;
  public final int status;

  public BusinessException(String code, String message, int status) {
    super(message);
    this.code = code;
    this.status = status;
  }

  public static BusinessException invalid(String message) {
    return new BusinessException("VALIDATION_FAILED", message, 422);
  }

  public static BusinessException missing() {
    return new BusinessException(
        "NOT_FOUND", "Không tìm thấy tài nguyên trong phạm vi được cấp.", 404);
  }

  public static BusinessException conflict(String code, String message) {
    return new BusinessException(code, message, 409);
  }

  public static void require(boolean condition, String message) {
    if (!condition) throw invalid(message);
  }
}
