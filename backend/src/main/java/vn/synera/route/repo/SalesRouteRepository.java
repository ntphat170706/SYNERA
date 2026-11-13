package vn.synera.route.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.route.domain.SalesRoute;

public interface SalesRouteRepository
    extends JpaRepository<SalesRoute, Long>, JpaSpecificationExecutor<SalesRoute> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from SalesRoute e where e.id=:id")
  java.util.Optional<SalesRoute> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<SalesRoute> findByNppId(Long nppId);
}
