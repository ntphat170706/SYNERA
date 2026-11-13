package vn.synera.iam;

import java.util.*;

public final class Permissions {
  public static final Set<String> ALL =
      Set.of(
          "USER_MANAGE",
          "CATALOG_WRITE",
          "SELLIN_CREATE",
          "ORDER_APPROVE",
          "SALES_CREATE",
          "SALES_APPROVE",
          "CREDIT_WRITE",
          "WAREHOUSE_WRITE",
          "CLOSING_WRITE",
          "CRM_WRITE",
          "ROUTE_WRITE",
          "POLICY_WRITE",
          "TICKET_WRITE",
          "TRADE_APPROVE",
          "REPORT_READ",
          "DISPLAY_REVIEW",
          "DELIVERY_EXECUTE");
  public static final Set<String> ROLES =
      Set.of(
          "SYSTEM_ADMIN",
          "NSX_DIRECTOR",
          "REGION_MANAGER",
          "ACCOUNTANT_NSX",
          "TRADE_MARKETING",
          "CUSTOMER_SERVICE",
          "NPP_OWNER",
          "ACCOUNTANT_NPP",
          "WAREHOUSE_NPP",
          "SUPERVISOR",
          "SALES",
          "DRIVER",
          "STORE",
          "SCHEDULER");

  public static Set<String> of(String role) {
    return switch (role) {
      case "SYSTEM_ADMIN" -> ALL;
      case "SCHEDULER" -> Set.of("CLOSING_WRITE", "REPORT_READ");
      case "NSX_DIRECTOR" ->
          Set.of("REPORT_READ", "TRADE_APPROVE", "CLOSING_WRITE", "POLICY_WRITE");
      case "REGION_MANAGER" ->
          Set.of(
              "TRADE_APPROVE",
              "ORDER_APPROVE",
              "CATALOG_WRITE",
              "ROUTE_WRITE",
              "TICKET_WRITE",
              "REPORT_READ",
              "DISPLAY_REVIEW");
      case "ACCOUNTANT_NSX" ->
          Set.of("CREDIT_WRITE", "CLOSING_WRITE", "ORDER_APPROVE", "REPORT_READ");
      case "TRADE_MARKETING" ->
          Set.of("POLICY_WRITE", "TRADE_APPROVE", "DISPLAY_REVIEW", "REPORT_READ", "TICKET_WRITE");
      case "CUSTOMER_SERVICE" -> Set.of("TICKET_WRITE", "REPORT_READ");
      case "NPP_OWNER" ->
          Set.of(
              "SELLIN_CREATE",
              "SALES_CREATE",
              "SALES_APPROVE",
              "WAREHOUSE_WRITE",
              "CRM_WRITE",
              "TICKET_WRITE",
              "REPORT_READ",
              "DISPLAY_REVIEW");
      case "ACCOUNTANT_NPP" -> Set.of("TICKET_WRITE", "REPORT_READ");
      case "WAREHOUSE_NPP" -> Set.of("WAREHOUSE_WRITE", "TICKET_WRITE");
      case "SUPERVISOR" ->
          Set.of(
              "SALES_CREATE",
              "SALES_APPROVE",
              "CRM_WRITE",
              "ROUTE_WRITE",
              "TICKET_WRITE",
              "REPORT_READ",
              "DISPLAY_REVIEW");
      case "SALES" -> Set.of("SALES_CREATE", "CRM_WRITE", "TICKET_WRITE", "REPORT_READ");
      case "DRIVER" -> Set.of("DELIVERY_EXECUTE");
      default -> Set.of();
    };
  }

  public static boolean nsx(String role) {
    return Set.of(
            "SYSTEM_ADMIN",
            "NSX_DIRECTOR",
            "REGION_MANAGER",
            "ACCOUNTANT_NSX",
            "TRADE_MARKETING",
            "CUSTOMER_SERVICE",
            "SCHEDULER")
        .contains(role);
  }

  private Permissions() {}
}
