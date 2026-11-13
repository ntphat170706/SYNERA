package vn.synera.iam.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.iam.domain.OtpChallenge;

public interface OtpChallengeRepository
    extends JpaRepository<OtpChallenge, Long>, JpaSpecificationExecutor<OtpChallenge> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from OtpChallenge e where e.id=:id")
  java.util.Optional<OtpChallenge> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);
}
