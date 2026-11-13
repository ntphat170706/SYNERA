package vn.synera.closing;

import java.time.LocalDate;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.closing.repo.ReportPeriodRepository;
import vn.synera.common.BusinessException;

@Service
public class PeriodGuard {
  private final ReportPeriodRepository periods;
  private final jakarta.persistence.EntityManager em;

  public PeriodGuard(ReportPeriodRepository periods, jakarta.persistence.EntityManager em) {
    this.periods = periods;
    this.em = em;
  }

  @Transactional
  public void exclusive() {
    em.find(
        vn.synera.closing.domain.ReportCalendar.class,
        1L,
        jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
  }

  @Transactional
  public void assertOpen(LocalDate date) {
    var day =
        periods.findAll(
            (r, q, cb) ->
                cb.and(cb.equal(r.get("type"), "DAY"), cb.equal(r.get("startDate"), date)));
    if (day.isEmpty()) {
      exclusive();
      day =
          periods.findAll(
              (r, q, cb) ->
                  cb.and(cb.equal(r.get("type"), "DAY"), cb.equal(r.get("startDate"), date)));
      if (day.isEmpty()) {
        var p = new vn.synera.closing.domain.ReportPeriod();
        p.type = "DAY";
        p.startDate = date;
        p.endDate = date;
        p.status = "OPEN";
        periods.saveAndFlush(p);
      }
    }
    em.find(
        vn.synera.closing.domain.ReportCalendar.class,
        1L,
        jakarta.persistence.LockModeType.PESSIMISTIC_READ);
    for (var p :
        periods.findAll(
            (r, q, cb) ->
                cb.and(
                    cb.lessThanOrEqualTo(r.get("startDate"), date),
                    cb.greaterThanOrEqualTo(r.get("endDate"), date))))
      if (Set.of("CLOSED", "LOCKED", "PUBLISHED").contains(p.status))
        throw BusinessException.conflict(
            "PERIOD_LOCKED", "Kỳ đã chốt; tạo phiếu điều chỉnh vào kỳ hiện tại.");
  }
}
