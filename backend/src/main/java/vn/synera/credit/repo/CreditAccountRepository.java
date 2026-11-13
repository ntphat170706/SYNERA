package vn.synera.credit.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.credit.domain.CreditAccount;

public interface CreditAccountRepository
    extends JpaRepository<CreditAccount, Long>, JpaSpecificationExecutor<CreditAccount> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from CreditAccount e where e.id=:id")
  java.util.Optional<CreditAccount> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<CreditAccount> findByNppId(Long nppId);

  java.util.Optional<CreditAccount> findFirstByNppId(Long nppId);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select a from CreditAccount a where a.nppId=:nppId")
  java.util.Optional<CreditAccount> lockByNppId(
      @org.springframework.data.repository.query.Param("nppId") Long nppId);
}
