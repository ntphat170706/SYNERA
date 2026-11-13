package vn.synera.sellin;

import java.util.*;
import vn.synera.common.BusinessException;

public enum OrderStatus {
  DRAFT,
  SUBMITTED,
  ON_HOLD_CREDIT,
  PENDING_APPROVAL,
  NEED_INFO,
  APPROVED,
  CONFIRMED_QTY,
  SHIPPED,
  IN_DELIVERY,
  DELIVERED,
  RECEIVED,
  RECEIVED_DISCREPANCY,
  AUTO_RECEIVED,
  RECONCILED,
  CLOSED,
  REJECTED,
  CANCELLED;
  private static final Map<OrderStatus, Set<OrderStatus>> NEXT =
      Map.ofEntries(
          Map.entry(DRAFT, Set.of(SUBMITTED, CANCELLED)),
          Map.entry(SUBMITTED, Set.of(PENDING_APPROVAL, ON_HOLD_CREDIT)),
          Map.entry(
              ON_HOLD_CREDIT, Set.of(PENDING_APPROVAL, APPROVED, REJECTED, NEED_INFO, CANCELLED)),
          Map.entry(PENDING_APPROVAL, Set.of(APPROVED, REJECTED, NEED_INFO, CANCELLED)),
          Map.entry(NEED_INFO, Set.of(PENDING_APPROVAL, CANCELLED)),
          Map.entry(APPROVED, Set.of(CONFIRMED_QTY, CANCELLED)),
          Map.entry(CONFIRMED_QTY, Set.of(SHIPPED, CANCELLED)),
          Map.entry(SHIPPED, Set.of(IN_DELIVERY)),
          Map.entry(IN_DELIVERY, Set.of(DELIVERED)),
          Map.entry(DELIVERED, Set.of(RECEIVED, RECEIVED_DISCREPANCY, AUTO_RECEIVED)),
          Map.entry(RECEIVED, Set.of(RECONCILED)),
          Map.entry(RECEIVED_DISCREPANCY, Set.of(RECONCILED)),
          Map.entry(AUTO_RECEIVED, Set.of(RECONCILED)),
          Map.entry(RECONCILED, Set.of(CLOSED)));

  public void assertNext(OrderStatus next) {
    if (!NEXT.getOrDefault(this, Set.of()).contains(next))
      throw BusinessException.conflict("INVALID_STATE_TRANSITION", name() + " -> " + next);
  }

  public static final Set<String> CREDIT_EXPOSURE =
      Set.of("ON_HOLD_CREDIT", "PENDING_APPROVAL", "NEED_INFO", "APPROVED", "CONFIRMED_QTY");
}
