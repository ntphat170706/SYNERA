package vn.synera.catalog.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.catalog.domain.StoreEntity;

public interface StoreEntityRepository
    extends JpaRepository<StoreEntity, Long>, JpaSpecificationExecutor<StoreEntity> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from StoreEntity e where e.id=:id")
  java.util.Optional<StoreEntity> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<StoreEntity> findByNppId(Long nppId);

  java.util.Optional<StoreEntity> findByNppIdAndPhone(Long nppId, String phone);

  java.util.List<StoreEntity> findByRepId(Long repId);
}
