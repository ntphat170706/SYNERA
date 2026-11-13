package vn.synera.salesorder.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.salesorder.domain.SalesLine;

public interface SalesLineRepository
    extends JpaRepository<SalesLine, Long>, JpaSpecificationExecutor<SalesLine> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from SalesLine e where e.id=:id")
  java.util.Optional<SalesLine> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<SalesLine> findByOrderId(Long orderId);

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query("delete from SalesLine l where l.orderId=:id")
  void removeByOrderId(@org.springframework.data.repository.query.Param("id") Long id);
}
