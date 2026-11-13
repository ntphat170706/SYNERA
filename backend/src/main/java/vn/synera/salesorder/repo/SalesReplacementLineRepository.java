package vn.synera.salesorder.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.synera.salesorder.domain.SalesReplacementLine;

public interface SalesReplacementLineRepository extends JpaRepository<SalesReplacementLine, Long> {
  java.util.List<SalesReplacementLine> findByReplacementId(Long id);
}
