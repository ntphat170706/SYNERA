package vn.synera.crm.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.crm.domain.Campaign;

public interface CampaignRepository
    extends JpaRepository<Campaign, Long>, JpaSpecificationExecutor<Campaign> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from Campaign e where e.id=:id")
  java.util.Optional<Campaign> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<Campaign> findByNppId(Long nppId);
}
