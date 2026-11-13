package vn.synera.credit.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.credit.domain.LedgerEntry;

public interface LedgerEntryRepository
    extends JpaRepository<LedgerEntry, Long>, JpaSpecificationExecutor<LedgerEntry> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from LedgerEntry e where e.id=:id")
  java.util.Optional<LedgerEntry> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<LedgerEntry> findByNppId(Long nppId);
}
