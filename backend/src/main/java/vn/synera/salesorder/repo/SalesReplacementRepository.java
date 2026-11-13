package vn.synera.salesorder.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.salesorder.domain.SalesReplacement;

public interface SalesReplacementRepository
    extends JpaRepository<SalesReplacement, Long>, JpaSpecificationExecutor<SalesReplacement> {
  java.util.List<SalesReplacement> findByOrderId(Long orderId);

  java.util.List<SalesReplacement> findByNppId(Long nppId);
}
