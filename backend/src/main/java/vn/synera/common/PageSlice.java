package vn.synera.common;

import java.util.List;
import org.springframework.data.domain.*;

public record PageSlice<T>(
    List<T> content, int page, int size, long totalElements, boolean hasNext) {
  public static Pageable request(int page, int size) {
    BusinessException.require(
        page >= 0 && page <= 100000 && size >= 1 && size <= 100,
        "Trang phải không âm, kích thước 1–100.");
    return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
  }

  public static <T> PageSlice<T> from(Page<T> source) {
    return new PageSlice<>(
        source.getContent(),
        source.getNumber(),
        source.getSize(),
        source.getTotalElements(),
        source.hasNext());
  }
}
