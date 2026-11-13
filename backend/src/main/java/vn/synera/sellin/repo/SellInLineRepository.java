package vn.synera.sellin.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.sellin.domain.SellInLine;

public interface SellInLineRepository
    extends JpaRepository<SellInLine, Long>, JpaSpecificationExecutor<SellInLine> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from SellInLine e where e.id=:id")
  java.util.Optional<SellInLine> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<SellInLine> findByOrderId(Long orderId);

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query("delete from SellInLine l where l.orderId=:id")
  void removeByOrderId(@org.springframework.data.repository.query.Param("id") Long id);
}
