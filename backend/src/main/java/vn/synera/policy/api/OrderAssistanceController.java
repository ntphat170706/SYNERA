package vn.synera.policy.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.common.ApiResponse;
import vn.synera.policy.OrderRequestParser;
import vn.synera.policy.PricingFacade;

@RestController
@RequestMapping("/api/v1/catalog/order-assistance")
public class OrderAssistanceController {
  private final PricingFacade pricing;

  public OrderAssistanceController(PricingFacade pricing) {
    this.pricing = pricing;
  }

  public record Request(
      @NotNull Long nppId, Long storeId, @NotBlank @Size(max = 4000) String text) {}

  @PostMapping
  @PreAuthorize("hasAnyAuthority('SELLIN_CREATE','SALES_CREATE')")
  public ApiResponse<?> suggest(@Valid @RequestBody Request r) {
    return ApiResponse.ok(
        OrderRequestParser.parse(r.text(), pricing.offerings(r.nppId(), r.storeId())));
  }
}
