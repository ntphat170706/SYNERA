package vn.synera.notify.repo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.synera.notify.domain.ApprovalRequest;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {
  List<ApprovalRequest> findByEntityTypeAndEntityIdOrderByStepNo(String entityType, Long entityId);
}
