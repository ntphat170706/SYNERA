package vn.synera.route.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.route.domain.RouteStop;

public interface RouteStopRepository
    extends JpaRepository<RouteStop, Long>, JpaSpecificationExecutor<RouteStop> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from RouteStop e where e.id=:id")
  java.util.Optional<RouteStop> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);
}
