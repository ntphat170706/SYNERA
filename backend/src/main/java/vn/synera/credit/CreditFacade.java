package vn.synera.credit;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.closing.PeriodGuard;
import vn.synera.common.*;
import vn.synera.credit.domain.*;
import vn.synera.credit.repo.*;
import vn.synera.iam.ActorFacade;
import vn.synera.notify.EventFacade;

@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class CreditFacade {
  public record Check(
      boolean hold, boolean warning, BigDecimal available, BigDecimal exposure, boolean overdue) {}

  public record Account(
      Long nppId,
      BigDecimal limitAmount,
      BigDecimal balance,
      BigDecimal overdueAmount,
      BigDecimal baseLimit,
      Instant temporaryUntil,
      int overdueDaysThreshold) {}

  private final CreditAccountRepository accounts;
  private final LedgerEntryRepository ledger;
  private final InvoiceRepository invoices;
  private final PaymentRepository payments;
  private final CatalogFacade catalog;
  private final ActorFacade actors;
  private final AuditService audit;
  private final PeriodGuard guard;
  private final EventFacade events;
  private final vn.synera.iam.SystemParametersFacade settings;

  public CreditFacade(
      CreditAccountRepository accounts,
      LedgerEntryRepository ledger,
      InvoiceRepository invoices,
      PaymentRepository payments,
      CatalogFacade catalog,
      ActorFacade actors,
      AuditService audit,
      PeriodGuard guard,
      EventFacade events,
      vn.synera.iam.SystemParametersFacade settings) {
    this.accounts = accounts;
    this.ledger = ledger;
    this.invoices = invoices;
    this.payments = payments;
    this.catalog = catalog;
    this.actors = actors;
    this.audit = audit;
    this.guard = guard;
    this.events = events;
    this.settings = settings;
  }

  private CreditAccount lock(Long nppId) {
    catalog.npp(nppId);
    return accounts.lockByNppId(nppId).orElseThrow(BusinessException::missing);
  }

  public void lockExposure(Long nppId) {
    lock(nppId);
  }

  public Check check(Long nppId, BigDecimal amount, BigDecimal inflight) {
    var a = lock(nppId);
    var exposure = a.balance.add(inflight).add(amount);
    var limit = effective(a);
    boolean overdue =
        invoices.findByNppId(nppId).stream()
            .anyMatch(
                i ->
                    i.remaining.signum() > 0
                        && i.dueDate.isBefore(Dates.today().minusDays(a.overdueDaysThreshold)));
    return new Check(
        overdue || exposure.compareTo(limit) > 0,
        exposure.compareTo(
                limit.multiply(settings.decimal("credit-warn-ratio", new BigDecimal("0.80"))))
            >= 0,
        limit.subtract(a.balance).subtract(inflight),
        exposure,
        overdue);
  }

  public Account account(Long nppId) {
    catalog.npp(nppId);
    var a = accounts.findFirstByNppId(nppId).orElseThrow(BusinessException::missing);
    var overdue =
        invoices.findByNppId(nppId).stream()
            .filter(i -> i.remaining.signum() > 0 && i.dueDate.isBefore(Dates.today()))
            .map(i -> i.remaining)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new Account(
        nppId,
        effective(a),
        a.balance,
        overdue,
        a.limitAmount,
        a.temporaryUntil,
        a.overdueDaysThreshold);
  }

  public List<Account> accounts() {
    return catalog.visibleNppIds().stream().map(this::account).toList();
  }

  public Object limit(Long nppId, BigDecimal amount, String reason) {

    var a = lock(nppId);
    BusinessException.require(
        amount.signum() >= 0 && amount.stripTrailingZeros().scale() <= 2,
        "Hạn mức không âm và tối đa 2 số lẻ.");
    var before = a.limitAmount;
    a.limitAmount = amount;
    audit.add(
        actors.current().id(),
        "CreditLimit",
        a.id,
        "UPDATE",
        Map.of("before", before, "after", amount, "reason", reason));
    return account(nppId);
  }

  public void invoice(Long nppId, Long orderId, BigDecimal amount) {
    guard.assertOpen(Dates.today());
    var a = lock(nppId);
    var i = new Invoice();
    i.nppId = nppId;
    i.orderId = orderId;
    i.amount = amount;
    i.remaining = amount.subtract(a.balance.negate().max(BigDecimal.ZERO).min(amount));
    i.dueDate = Dates.today().plusDays(30);
    i.status = i.remaining.signum() == 0 ? "PAID" : "OPEN";
    invoices.saveAndFlush(i);
    post(a, "INVOICE", i.id, amount, BigDecimal.ZERO);
  }

  private void post(CreditAccount a, String type, Long id, BigDecimal debit, BigDecimal credit) {
    a.balance = a.balance.add(debit).subtract(credit);
    var l = new LedgerEntry();
    l.nppId = a.nppId;
    l.docType = type;
    l.docId = id;
    l.debit = debit;
    l.credit = credit;
    l.balanceAfter = a.balance;
    l.postedAt = Instant.now();
    ledger.save(l);
  }

  public Object pay(Long nppId, BigDecimal amount, String reference) {
    guard.assertOpen(Dates.today());
    var a = lock(nppId);
    BusinessException.require(
        amount.signum() > 0 && amount.compareTo(a.balance) <= 0,
        "Số tiền phải dương và không vượt số dư nợ hiện tại.");
    var p = new Payment();
    p.nppId = nppId;
    p.reference = reference;
    p.amount = amount;
    p.status = "CONFIRMED";
    payments.saveAndFlush(p);
    var outstanding =
        invoices.findByNppId(nppId).stream()
            .filter(i -> i.remaining.signum() > 0)
            .sorted(Comparator.comparing((Invoice i) -> i.dueDate).thenComparing(i -> i.id))
            .toList();
    BigDecimal remaining = amount;
    for (var candidate : outstanding) {
      var i = invoices.lockById(candidate.id).orElseThrow();
      var take = i.remaining.min(remaining);
      i.remaining = i.remaining.subtract(take);
      if (i.remaining.signum() == 0) i.status = "PAID";
      remaining = remaining.subtract(take);
      if (remaining.signum() == 0) break;
    }
    BusinessException.require(
        remaining.signum() == 0, "Sổ công nợ chưa khớp hóa đơn, cần đối soát trước khi ghi thu.");
    post(a, "PAYMENT", p.id, BigDecimal.ZERO, amount);
    audit.add(
        actors.current().id(),
        "Payment",
        p.id,
        "CONFIRM",
        Map.of("nppId", nppId, "amount", amount, "reference", reference));
    events.add("PaymentConfirmed", p.id, nppId, actors.current().id(), Map.of("amount", amount));
    return Map.of("id", p.id, "balance", a.balance);
  }

  public void refund(Long nppId, Long returnId, Long orderId, BigDecimal amount) {
    guard.assertOpen(Dates.today());
    var account = lock(nppId);
    var original =
        invoices.findByOrderId(orderId).stream()
            .findFirst()
            .orElseThrow(BusinessException::missing);
    var invoice = invoices.lockById(original.id).orElseThrow();
    invoice.remaining = invoice.remaining.subtract(amount.min(invoice.remaining));
    if (invoice.remaining.signum() == 0) invoice.status = "PAID";
    post(account, "RETURN", returnId, BigDecimal.ZERO, amount);
  }

  public void adjust(Long nppId, Long adjustmentId, BigDecimal delta) {
    guard.assertOpen(Dates.today());
    var a = lock(nppId);
    if (delta.signum() > 0) {
      var invoice = new Invoice();
      invoice.nppId = nppId;
      invoice.amount = delta;
      invoice.remaining = delta;
      invoice.dueDate = Dates.today().plusDays(30);
      invoice.status = "OPEN";
      invoices.save(invoice);
    } else {
      BigDecimal remaining = delta.negate();
      for (var i :
          invoices.findByNppId(nppId).stream()
              .filter(x -> x.remaining.signum() > 0)
              .sorted(java.util.Comparator.comparing(x -> x.id))
              .toList()) {
        var take = i.remaining.min(remaining);
        i.remaining = i.remaining.subtract(take);
        if (i.remaining.signum() == 0) i.status = "PAID";
        remaining = remaining.subtract(take);
        if (remaining.signum() == 0) break;
      }
    }
    post(
        a,
        "ADJUSTMENT",
        adjustmentId,
        delta.max(BigDecimal.ZERO),
        delta.negate().max(BigDecimal.ZERO));
  }

  public List<Map<String, Object>> ledger(Long nppId) {
    catalog.npp(nppId);
    return ledger.findByNppId(nppId).stream()
        .sorted(Comparator.comparing(l -> l.id))
        .map(
            l ->
                Map.<String, Object>of(
                    "id",
                    l.id,
                    "docType",
                    l.docType,
                    "docId",
                    l.docId,
                    "debit",
                    l.debit,
                    "credit",
                    l.credit,
                    "balanceAfter",
                    l.balanceAfter,
                    "postedAt",
                    l.postedAt))
        .toList();
  }

  public List<Map<String, Object>> invoices(Long nppId) {
    catalog.npp(nppId);
    return invoices.findByNppId(nppId).stream()
        .map(
            i ->
                Map.<String, Object>of(
                    "id",
                    i.id,
                    "orderId",
                    Objects.requireNonNullElse(i.orderId, 0L),
                    "amount",
                    i.amount,
                    "remaining",
                    i.remaining,
                    "dueDate",
                    i.dueDate,
                    "status",
                    i.status))
        .toList();
  }

  public void initialize(Long nppId, BigDecimal limit) {
    catalog.npp(nppId);
    BusinessException.require(limit != null && limit.signum() >= 0, "Hạn mức không hợp lệ.");
    BusinessException.require(accounts.findByNppId(nppId).isEmpty(), "Đã có tài khoản công nợ.");
    var account = new CreditAccount();
    account.nppId = nppId;
    account.limitAmount = limit;
    account.overdueDaysThreshold = 0;
    accounts.saveAndFlush(account);
  }

  public void seed() {
    if (accounts.count() > 0) return;
    for (long id = 2; id <= 3; id++) {
      var a = new CreditAccount();
      a.nppId = id;
      a.limitAmount = new BigDecimal("500000000");
      a.overdueDaysThreshold = 0;
      accounts.save(a);
    }
  }

  private BigDecimal effective(CreditAccount account) {
    return account.temporaryLimit != null
            && account.temporaryUntil != null
            && account.temporaryUntil.isAfter(Instant.now())
        ? account.limitAmount.max(account.temporaryLimit)
        : account.limitAmount;
  }

  public Object temporaryLimit(Long npp, BigDecimal amount, Instant until, String reason) {
    var a = lock(npp);
    BusinessException.require(
        amount != null
            && amount.compareTo(a.limitAmount) >= 0
            && amount.stripTrailingZeros().scale() <= 2
            && until != null
            && until.isAfter(Instant.now())
            && !until.isAfter(Instant.now().plusSeconds(31L * 86400)),
        "Nâng hạn mức tối đa 31 ngày, số tiền không thấp hơn hạn mức cơ sở và cần lý do.");
    a.temporaryLimit = amount;
    a.temporaryUntil = until;
    audit.add(
        actors.current().id(),
        "CreditLimit",
        a.id,
        "TEMPORARY_LIMIT",
        Map.of("amount", amount, "until", until, "reason", reason));
    return account(npp);
  }

  public Object terms(Long npp, int days, String reason) {
    BusinessException.require(days >= 0 && days <= 365, "Ngưỡng quá hạn 0–365 ngày và cần lý do.");
    var a = lock(npp);
    a.overdueDaysThreshold = days;
    audit.add(
        actors.current().id(),
        "CreditLimit",
        a.id,
        "OVERDUE_TERMS",
        Map.of("days", days, "reason", reason));
    return account(npp);
  }
}
