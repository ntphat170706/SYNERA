package vn.synera.iam.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.iam.domain.AppUser;

public interface AppUserRepository
    extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from AppUser e where e.id=:id")
  java.util.Optional<AppUser> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<AppUser> findByNppId(Long nppId);

  java.util.Optional<AppUser> findByUsername(String username);
}
