package vn.synera.iam.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.iam.domain.RefreshSession;

public interface RefreshSessionRepository
    extends JpaRepository<RefreshSession, Long>, JpaSpecificationExecutor<RefreshSession> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from RefreshSession e where e.id=:id")
  java.util.Optional<RefreshSession> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.Optional<RefreshSession> findByTokenHash(String tokenHash);

  java.util.List<RefreshSession> findByUserId(Long userId);

  @org.springframework.data.jpa.repository.Query(
      "select t.userId from RefreshSession t where t.tokenHash=:hash")
  java.util.Optional<Long> userIdByHash(
      @org.springframework.data.repository.query.Param("hash") String hash);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select t from RefreshSession t where t.tokenHash=:hash")
  java.util.Optional<RefreshSession> lockByHash(
      @org.springframework.data.repository.query.Param("hash") String hash);
}
