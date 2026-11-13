package vn.synera.salesorder.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.salesorder.domain.DailyReport;

public interface DailyReportRepository
    extends JpaRepository<DailyReport, Long>, JpaSpecificationExecutor<DailyReport> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from DailyReport e where e.id=:id")
  java.util.Optional<DailyReport> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<DailyReport> findByNppId(Long nppId);
}
