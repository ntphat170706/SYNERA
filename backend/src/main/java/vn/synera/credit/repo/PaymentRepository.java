package vn.synera.credit.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.credit.domain.Payment;

public interface PaymentRepository
    extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from Payment e where e.id=:id")
  java.util.Optional<Payment> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<Payment> findByNppId(Long nppId);
}
