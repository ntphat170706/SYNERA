package vn.synera.report.api;

import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.common.*;
import vn.synera.report.ManufacturerFacade;

@RestController
@RequestMapping("/api/v1/manufacturer")
@PreAuthorize(
    "hasAuthority('REPORT_READ') and !hasRole('SALES') and !hasRole('NPP_OWNER') and"
        + " !hasRole('WAREHOUSE_NPP') and !hasRole('ACCOUNTANT_NPP')")
public class ManufacturerController {
  private final ManufacturerFacade facade;

  public ManufacturerController(ManufacturerFacade facade) {
    this.facade = facade;
  }

  @GetMapping("/network")
  public ApiResponse<?> network(
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    return ApiResponse.ok(
        facade.network(
            from == null ? Dates.today().withDayOfMonth(1) : from,
            to == null ? Dates.today() : to));
  }

  @GetMapping("/npps/{id}/360")
  public ApiResponse<?> profile(
      @PathVariable Long id,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    return ApiResponse.ok(
        facade.nppProfile(
            id,
            from == null ? Dates.today().withDayOfMonth(1) : from,
            to == null ? Dates.today() : to));
  }
}
