package vn.synera.delivery.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.delivery.domain.DeliveryTrip;

public interface DeliveryTripRepository
    extends JpaRepository<DeliveryTrip, Long>, JpaSpecificationExecutor<DeliveryTrip> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from DeliveryTrip e where e.id=:id")
  java.util.Optional<DeliveryTrip> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<DeliveryTrip> findByNppId(Long nppId);
}
