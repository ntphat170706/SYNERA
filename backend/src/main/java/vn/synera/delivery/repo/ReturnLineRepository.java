package vn.synera.delivery.repo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.synera.delivery.domain.ReturnLine;

public interface ReturnLineRepository extends JpaRepository<ReturnLine, Long> {
  List<ReturnLine> findByReturnId(Long returnId);

  List<ReturnLine> findByOrderLineId(Long orderLineId);
}
