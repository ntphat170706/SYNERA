package vn.synera.inventory.repo;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import vn.synera.inventory.domain.Stocktake;

public interface StocktakeRepository
    extends JpaRepository<Stocktake, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Stocktake> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Stocktake s where s.id=:id")
  Optional<Stocktake> lockById(@org.springframework.data.repository.query.Param("id") Long id);
}
