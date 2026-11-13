package vn.synera.policy.repo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.synera.policy.domain.AppliedBenefit;

public interface AppliedBenefitRepository extends JpaRepository<AppliedBenefit, Long> {
  List<AppliedBenefit> findByDocumentTypeAndDocumentId(String documentType, Long documentId);
}
