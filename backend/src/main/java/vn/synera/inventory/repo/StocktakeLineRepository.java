package vn.synera.inventory.repo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.synera.inventory.domain.StocktakeLine;

public interface StocktakeLineRepository extends JpaRepository<StocktakeLine, Long> {
  List<StocktakeLine> findByStocktakeId(Long stocktakeId);
}
