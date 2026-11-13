package vn.synera.policy;

import java.math.*;
import java.util.*;

/** Pure deterministic pricing. Quantities in promotion thresholds use base units. */
public final class PricingEngine {
  public record Candidate(
      Long id,
      Long skuId,
      BigDecimal minQty,
      BigDecimal minAmount,
      BigDecimal percent,
      int priority,
      String group,
      BigDecimal remaining,
      Map<Long, BigDecimal> requiredBase,
      long remainingUses) {
    public Candidate(
        Long id,
        Long skuId,
        BigDecimal minQty,
        BigDecimal minAmount,
        BigDecimal percent,
        int priority,
        String group,
        BigDecimal remaining) {
      this(
          id,
          skuId,
          minQty,
          minAmount,
          percent,
          priority,
          group,
          remaining,
          Map.of(),
          Long.MAX_VALUE);
    }
  }

  public record Item(Long skuId, String uom, BigDecimal qty, BigDecimal factor, BigDecimal price) {
    public BigDecimal subtotal() {
      return qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }
  }

  public record Benefit(Long promotionId, BigDecimal amount) {}

  public record Result(
      BigDecimal subtotal,
      BigDecimal discount,
      BigDecimal total,
      List<Benefit> benefits,
      List<BigDecimal> lineDiscounts) {}

  public Result price(List<Item> items, List<Candidate> candidates) {
    BigDecimal
        subtotal =
            items.stream()
                .map(Item::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP),
        discount = BigDecimal.ZERO;
    var groups = new HashSet<String>();
    var benefits = new ArrayList<Benefit>();
    var lineDiscounts =
        new ArrayList<BigDecimal>(Collections.nCopies(items.size(), BigDecimal.ZERO));
    for (var c :
        candidates.stream()
            .sorted(
                Comparator.comparingInt(Candidate::priority)
                    .reversed()
                    .thenComparing(Candidate::id))
            .toList()) {
      if (c.remainingUses() <= 0) continue;
      if (c.requiredBase().entrySet().stream()
          .anyMatch(
              required ->
                  items.stream()
                          .filter(i -> i.skuId().equals(required.getKey()))
                          .map(i -> i.qty().multiply(i.factor()))
                          .reduce(BigDecimal.ZERO, BigDecimal::add)
                          .compareTo(required.getValue())
                      < 0)) continue;
      if (c.percent().signum() < 0 || c.percent().compareTo(new BigDecimal("100")) > 0)
        throw new IllegalArgumentException("Invalid promotion percentage");
      if (c.group() != null && !c.group().isBlank() && groups.contains(c.group())) continue;
      var eligible =
          items.stream().filter(i -> c.skuId() == null || c.skuId().equals(i.skuId())).toList();
      var qty =
          eligible.stream()
              .map(i -> i.qty().multiply(i.factor()))
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      var amount = eligible.stream().map(Item::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
      if (qty.compareTo(c.minQty()) < 0 || amount.compareTo(c.minAmount()) < 0) continue;
      var indices = new ArrayList<Integer>();
      for (int i = 0; i < items.size(); i++)
        if (c.skuId() == null || c.skuId().equals(items.get(i).skuId())) indices.add(i);
      var remaining =
          indices.stream()
              .map(i -> items.get(i).subtotal().subtract(lineDiscounts.get(i)))
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      var benefit =
          amount
              .multiply(c.percent())
              .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
              .min(remaining);
      if (benefit.signum() <= 0 || benefit.compareTo(c.remaining()) > 0) continue;
      BigDecimal unassigned = benefit;
      for (int n = 0; n < indices.size(); n++) {
        int index = indices.get(n);
        BigDecimal free = items.get(index).subtotal().subtract(lineDiscounts.get(index));
        BigDecimal allocated =
            n == indices.size() - 1
                ? unassigned
                : free.multiply(benefit).divide(remaining, 2, RoundingMode.DOWN);
        allocated = allocated.min(free).min(unassigned);
        lineDiscounts.set(index, lineDiscounts.get(index).add(allocated));
        unassigned = unassigned.subtract(allocated);
      }
      BigDecimal applied = benefit.subtract(unassigned);
      benefits.add(new Benefit(c.id(), applied));
      discount = discount.add(applied);
      if (c.group() != null && !c.group().isBlank()) groups.add(c.group());
    }
    return new Result(
        subtotal,
        discount,
        subtotal.subtract(discount),
        List.copyOf(benefits),
        List.copyOf(lineDiscounts));
  }
}
