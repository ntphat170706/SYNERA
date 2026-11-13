package vn.synera.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class DocumentRequests {
  public record Quantity(
      @jakarta.validation.constraints.NotNull Long skuId,
      @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0")
          java.math.BigDecimal qtyBase) {}

  private DocumentRequests() {}

  public record Line(
      @NotNull Long skuId,
      @NotBlank @Size(max = 20) String uom,
      @NotNull @DecimalMin("0.001") @Digits(integer = 15, fraction = 3) BigDecimal qty) {}

  public record Order(
      @NotNull Long nppId, @NotEmpty @Size(max = 100) List<@Valid Line> lines, Long storeId) {
    public Order(Long nppId, List<Line> lines) {
      this(nppId, lines, null);
    }
  }

  public record Reason(@Size(max = 2000) String reason) {
    public Reason {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  public record ReceiptLine(
      @NotNull Long lineId,
      @NotNull @DecimalMin("0") BigDecimal received,
      @NotNull @DecimalMin("0") BigDecimal damaged,
      @NotNull @DecimalMin("0") BigDecimal returned) {}

  public record Receipt(
      @NotEmpty List<@Valid ReceiptLine> lines,
      @Size(max = 2000) String reason,
      List<Long> attachmentIds) {
    public Receipt {
      reason = java.util.Objects.toString(reason, "");
    }
  }
}
