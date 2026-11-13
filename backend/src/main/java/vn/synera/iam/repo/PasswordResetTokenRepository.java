package vn.synera.iam.repo;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import vn.synera.iam.domain.PasswordResetToken;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<PasswordResetToken> findByTokenHash(String tokenHash);

  @Query("select t.userId from PasswordResetToken t where t.tokenHash=:hash")
  Optional<Long> userIdByHash(@org.springframework.data.repository.query.Param("hash") String hash);

  long countByUserIdAndCreatedAtAfter(Long userId, Instant after);

  List<PasswordResetToken> findByUserId(Long userId);
}
