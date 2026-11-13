package vn.synera.credit.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.credit.domain.Invoice;

public interface InvoiceRepository
    extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from Invoice e where e.id=:id")
  java.util.Optional<Invoice> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<Invoice> findByNppId(Long nppId);

  java.util.List<Invoice> findByOrderId(Long orderId);
}
