package vn.synera.display;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.display.repo.DisplayAuditRepository;

@Service
@Transactional(readOnly = true)
public class DisplayReadFacade {
  private final CatalogFacade catalog;
  private final DisplayAuditRepository audits;

  public DisplayReadFacade(CatalogFacade catalog, DisplayAuditRepository audits) {
    this.catalog = catalog;
    this.audits = audits;
  }

  public PageSlice<Map<String, Object>> storePage(Long storeId, int page, int size) {
    catalog.store(storeId);
    return PageSlice.from(
        audits
            .findAll(
                (r, q, cb) -> cb.equal(r.get("storeId"), storeId), PageSlice.request(page, size))
            .map(
                a ->
                    SqlRecords.mutable(
                        "id",
                        a.id,
                        "status",
                        a.status,
                        "provider",
                        a.provider,
                        "finalScore",
                        a.finalScore,
                        "aiScore",
                        a.aiScore,
                        "confidence",
                        a.confidence,
                        "attachmentId",
                        a.attachmentId,
                        "modelVersion",
                        a.modelVersion,
                        "createdAt",
                        a.createdAt)));
  }
}
