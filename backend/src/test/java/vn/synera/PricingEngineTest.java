package vn.synera;

import static org.junit.jupiter.api.Assertions.*;

import java.math.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.synera.policy.PricingEngine;

class PricingEngineTest {
  @Test
  void comboRequiresEverySkuAndHonorsUseLimit() {
    var engine = new PricingEngine();
    var one =
        List.of(
            new PricingEngine.Item(
                1L, "BASE", new BigDecimal("24"), BigDecimal.ONE, new BigDecimal("10")));
    var combination =
        new PricingEngine.Candidate(
            1L,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("10"),
            1,
            null,
            new BigDecimal("1000"),
            Map.of(1L, new BigDecimal("24"), 2L, new BigDecimal("6")),
            1);
    assertEquals(0, engine.price(one, List.of(combination)).discount().signum());
    var both = new ArrayList<>(one);
    both.add(
        new PricingEngine.Item(
            2L, "BASE", new BigDecimal("6"), BigDecimal.ONE, new BigDecimal("10")));
    assertEquals(
        0, new BigDecimal("30.00").compareTo(engine.price(both, List.of(combination)).discount()));
    var exhausted =
        new PricingEngine.Candidate(
            1L,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("10"),
            1,
            null,
            new BigDecimal("1000"),
            Map.of(),
            0);
    assertEquals(0, engine.price(both, List.of(exhausted)).discount().signum());
  }

  final PricingEngine engine = new PricingEngine();
  final List<PricingEngine.Item> lines =
      List.of(
          new PricingEngine.Item(
              1L, "THUNG", new BigDecimal("10"), new BigDecimal("24"), new BigDecimal("100")));

  PricingEngine.Candidate promo(
      long id,
      String percent,
      String qty,
      String amount,
      String budget,
      int priority,
      String group) {
    return new PricingEngine.Candidate(
        id,
        null,
        new BigDecimal(qty),
        new BigDecimal(amount),
        new BigDecimal(percent),
        priority,
        group,
        new BigDecimal(budget));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 3, 5, 10, 25, 50, 75, 99, 100})
  void percentages(int percent) {
    var result =
        engine.price(lines, List.of(promo(1, String.valueOf(percent), "0", "0", "10000", 1, null)));
    assertEquals(0, new BigDecimal(1000 - percent * 10).compareTo(result.total()));
  }

  @Test
  void noPromotion() {
    assertEquals(0, new BigDecimal("1000").compareTo(engine.price(lines, List.of()).total()));
  }

  @Test
  void belowQuantity() {
    assertEquals(
        0,
        engine
            .price(lines, List.of(promo(1, "10", "241", "0", "1000", 1, null)))
            .discount()
            .signum());
  }

  @Test
  void exactQuantity() {
    assertEquals(
        0,
        new BigDecimal("100")
            .compareTo(
                engine
                    .price(lines, List.of(promo(1, "10", "240", "0", "1000", 1, null)))
                    .discount()));
  }

  @Test
  void belowAmount() {
    assertEquals(
        0,
        engine
            .price(lines, List.of(promo(1, "10", "0", "1001", "1000", 1, null)))
            .discount()
            .signum());
  }

  @Test
  void exactAmount() {
    assertEquals(
        0,
        new BigDecimal("100")
            .compareTo(
                engine
                    .price(lines, List.of(promo(1, "10", "0", "1000", "1000", 1, null)))
                    .discount()));
  }

  @Test
  void depletedBudget() {
    assertTrue(
        engine.price(lines, List.of(promo(1, "10", "0", "0", "0", 1, null))).benefits().isEmpty());
  }

  @Test
  void exactBudget() {
    assertEquals(
        1,
        engine.price(lines, List.of(promo(1, "10", "0", "0", "100", 1, null))).benefits().size());
  }

  @Test
  void insufficientBudget() {
    assertTrue(
        engine.price(lines, List.of(promo(1, "10", "0", "0", "99", 1, null))).benefits().isEmpty());
  }

  @Test
  void exclusiveGroupUsesPriority() {
    var r =
        engine.price(
            lines,
            List.of(
                promo(1, "10", "0", "0", "1000", 2, "A"),
                promo(2, "20", "0", "0", "1000", 1, "A")));
    assertEquals(1, r.benefits().size());
    assertEquals(1L, r.benefits().get(0).promotionId());
  }

  @Test
  void stackingNeverProducesNegativeTotal() {
    var r =
        engine.price(
            lines,
            List.of(
                promo(1, "75", "0", "0", "1000", 2, null),
                promo(2, "75", "0", "0", "1000", 1, null)));
    assertEquals(0, r.total().signum());
    assertEquals(0, new BigDecimal("1000").compareTo(r.discount()));
  }

  @Test
  void skuPromotionNeverDiscountsOtherProducts() {
    var mixed =
        List.of(
            new PricingEngine.Item(
                1L, "CHAI", BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("100")),
            new PricingEngine.Item(
                2L, "CHAI", BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("900")));
    var candidate =
        new PricingEngine.Candidate(
            1L,
            1L,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("50"),
            1,
            null,
            new BigDecimal("1000"));
    var result = engine.price(mixed, List.of(candidate));
    assertEquals(0, new BigDecimal("50").compareTo(result.lineDiscounts().get(0)));
    assertEquals(0, result.lineDiscounts().get(1).signum());
    assertEquals(0, new BigDecimal("950").compareTo(result.total()));
  }

  @Test
  void roundingPreservesExactSumOfLineDiscounts() {
    var items =
        List.of(
            new PricingEngine.Item(
                1L, "CHAI", BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("0.01")),
            new PricingEngine.Item(
                2L, "CHAI", BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("0.02")));
    var result = engine.price(items, List.of(promo(1, "50", "0", "0", "1000", 1, null)));
    assertEquals(
        0,
        result
            .discount()
            .compareTo(result.lineDiscounts().stream().reduce(BigDecimal.ZERO, BigDecimal::add)));
  }
}
