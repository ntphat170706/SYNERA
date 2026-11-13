package vn.synera.catalog.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
  private final CatalogFacade catalog;
  private final IdempotencyService idem;

  public CatalogController(CatalogFacade catalog, IdempotencyService idem) {
    this.catalog = catalog;
    this.idem = idem;
  }

  public record NewStore(
      @NotNull Long nppId,
      @NotNull Long repId,
      @NotBlank String name,
      String owner,
      @NotBlank String phone,
      @NotBlank String address,
      Double latitude,
      Double longitude) {}

  public record NewProduct(
      @NotBlank String code,
      @NotBlank String name,
      @NotBlank String category,
      @NotBlank String baseUnit,
      @NotNull @DecimalMin("0.001") BigDecimal minOrder,
      @NotEmpty Map<String, BigDecimal> factors) {}

  @GetMapping("/organizations")
  public ApiResponse<?> orgs() {
    return ApiResponse.ok(catalog.npps());
  }

  @GetMapping("/catalog/skus")
  public ApiResponse<?> products(@RequestParam(defaultValue = "") String q) {
    return ApiResponse.ok(catalog.products(q));
  }

  @GetMapping("/catalog/skus/{id}")
  public ApiResponse<?> product(@PathVariable Long id) {
    return ApiResponse.ok(catalog.product(id));
  }

  @GetMapping("/catalog/skus/{id}/units")
  public ApiResponse<?> units(@PathVariable Long id) {
    return ApiResponse.ok(catalog.units(id));
  }

  @PostMapping("/catalog/skus")
  @PreAuthorize("hasAuthority('CATALOG_WRITE')")
  public ApiResponse<?> create(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody NewProduct r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "catalog.create",
            r,
            () ->
                catalog.createProduct(
                    r.code(), r.name(), r.category(), r.baseUnit(), r.minOrder(), r.factors())));
  }

  @GetMapping("/stores")
  public ApiResponse<?> stores(@RequestParam(defaultValue = "") String q) {
    return ApiResponse.ok(catalog.stores(q));
  }

  @GetMapping("/stores/{id}")
  public ApiResponse<?> store(@PathVariable Long id) {
    return ApiResponse.ok(catalog.store(id));
  }

  @PostMapping("/stores")
  @PreAuthorize("hasAuthority('CRM_WRITE')")
  public ApiResponse<?> createStore(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody NewStore r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "store.create",
            r,
            () ->
                catalog.createStore(
                    r.nppId(),
                    r.repId(),
                    r.name(),
                    r.owner(),
                    r.phone(),
                    r.address(),
                    r.latitude(),
                    r.longitude())));
  }

  @GetMapping("/warehouses")
  public ApiResponse<?> warehouses() {
    return ApiResponse.ok(catalog.warehouses());
  }
}
