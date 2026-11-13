package vn.synera.integration;

import io.minio.*;
import io.minio.http.Method;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.iam.ActorFacade;
import vn.synera.integration.domain.Attachment;
import vn.synera.integration.repo.AttachmentRepository;

@Service
@Transactional
public class StorageFacade {
  private final AttachmentRepository attachments;
  private final CatalogFacade catalog;
  private final ActorFacade actors;
  private final String endpoint, publicEndpoint, access, secret, bucket;
  private final boolean local;
  private final boolean filesystem;
  private final MalwareScanner scanner;
  private static final Set<String> TYPES =
      Set.of("image/png", "image/jpeg", "image/webp", "application/pdf");

  public StorageFacade(
      AttachmentRepository attachments,
      CatalogFacade catalog,
      ActorFacade actors,
      @Value("${synera.storage.endpoint}") String endpoint,
      @Value("${synera.storage.public-endpoint:}") String publicEndpoint,
      @Value("${synera.storage.access-key}") String access,
      @Value("${synera.storage.secret-key}") String secret,
      @Value("${synera.storage.bucket}") String bucket,
      @Value("${synera.storage.local-enabled:false}") boolean local,
      @Value("${synera.storage.filesystem-enabled:false}") boolean filesystem,
      MalwareScanner scanner) {
    this.attachments = attachments;
    this.catalog = catalog;
    this.actors = actors;
    this.endpoint = endpoint;
    this.publicEndpoint = publicEndpoint;
    this.access = access;
    this.secret = secret;
    this.bucket = bucket;
    this.local = local;
    this.filesystem = filesystem;
    this.scanner = scanner;
  }

  private MinioClient client() {
    if (endpoint.isBlank() || access.isBlank() || secret.isBlank())
      throw new BusinessException(
          "INTEGRATION_NOT_CONFIGURED", "Chưa cấu hình MinIO; không có URL upload thật.", 503);
    return MinioClient.builder().endpoint(endpoint).credentials(access, secret).build();
  }

  private Attachment owned(Long id) {
    var a = attachments.findById(id).orElseThrow(BusinessException::missing);
    catalog.npp(a.nppId);
    if (!actors.current().nsx()
        && !a.ownerId.equals(actors.current().id())
        && !actors.has("DISPLAY_REVIEW")
        && !actors.has("WAREHOUSE_WRITE")) throw BusinessException.missing();
    return a;
  }

  public Object presign(Long npp, String filename, String type, long size) {
    catalog.npp(npp);
    BusinessException.require(
        TYPES.contains(type) && size > 0 && size <= 5 * 1024 * 1024 && filename.length() <= 150,
        "Chỉ nhận PNG/JPG/WebP/PDF, tối đa 5 MB.");
    var a = new Attachment();
    a.nppId = npp;
    a.ownerId = actors.current().id();
    a.filename = Path.of(filename).getFileName().toString();
    a.contentType = type;
    a.sizeBytes = size;
    a.objectKey = npp + "/" + a.ownerId + "/" + UUID.randomUUID();
    a.status = "PENDING_UPLOAD";
    client();
    var minio =
        MinioClient.builder()
            .endpoint(publicEndpoint.isBlank() ? endpoint : publicEndpoint)
            .credentials(access, secret)
            .region("us-east-1")
            .build();
    try {
      String url =
          minio.getPresignedObjectUrl(
              GetPresignedObjectUrlArgs.builder()
                  .method(Method.PUT)
                  .bucket(bucket)
                  .object(a.objectKey)
                  .expiry(300)
                  .build());
      attachments.saveAndFlush(a);
      return Map.of("id", a.id, "uploadUrl", url, "expiresIn", 300, "requiredContentType", type);
    } catch (Exception e) {
      throw new BusinessException("STORAGE_UNAVAILABLE", "Không tạo được URL MinIO.", 503);
    }
  }

  public Object complete(Long id) {
    var a = owned(id);
    BusinessException.require(
        a.status.equals("PENDING_UPLOAD") || a.status.equals("PENDING_SCAN"), "Tệp đã được xử lý.");
    try {
      var stat =
          client().statObject(StatObjectArgs.builder().bucket(bucket).object(a.objectKey).build());
      BusinessException.require(
          stat.size() == a.sizeBytes && Objects.equals(stat.contentType(), a.contentType),
          "Kích thước/loại tệp không khớp khai báo.");
      byte[] bytes;
      try (var input =
          client().getObject(GetObjectArgs.builder().bucket(bucket).object(a.objectKey).build())) {
        bytes = input.readNBytes(5 * 1024 * 1024 + 1);
      }
      BusinessException.require(
          bytes.length == a.sizeBytes && magic(bytes, a.contentType),
          "Nội dung tệp không khớp khai báo.");
      a.contentHash = sha(bytes);
      if (!scanner.configured()) {
        a.status = "PENDING_SCAN";
        return Map.of("id", id, "status", a.status, "note", "Chưa cấu hình dịch vụ quét tệp.");
      }
      if (!scanner.clean(bytes)) {
        a.status = "QUARANTINED";
        a.scanResult = "MALWARE_DETECTED";
        return Map.of("id", id, "status", a.status);
      }
      // Move validated bytes to a fresh key. An old presigned PUT cannot alter an accepted file.
      String uploaded = a.objectKey;
      String accepted = a.nppId + "/accepted/" + UUID.randomUUID();
      client()
          .putObject(
              PutObjectArgs.builder()
                  .bucket(bucket)
                  .object(accepted)
                  .contentType(a.contentType)
                  .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                  .build());
      a.objectKey = accepted;
      a.status = "READY_SCANNED";
      a.scanResult = "CLAMAV_OK";
      client().removeObject(RemoveObjectArgs.builder().bucket(bucket).object(uploaded).build());
      return Map.of("id", id, "status", a.status, "scanResult", a.scanResult);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException("STORAGE_UNAVAILABLE", "Chưa tìm thấy object đã tải lên.", 503);
    }
  }

  public Object uploadLocal(Long npp, MultipartFile file) {
    if (!local)
      throw new BusinessException(
          "INTEGRATION_NOT_CONFIGURED", "Upload local bị tắt ngoài profile local/test.", 503);
    return uploadFile(npp, file);
  }

  public Object uploadFile(Long npp, MultipartFile file) {
    if (!local && !filesystem)
      throw new BusinessException(
          "INTEGRATION_NOT_CONFIGURED",
          "Kho tệp trực tiếp chưa được cấu hình; dùng upload S3.",
          503);
    if (!local && !scanner.configured())
      throw new BusinessException(
          "SCANNER_UNAVAILABLE",
          "Tệp cần được quét trước khi lưu trên môi trường chính thức.",
          503);
    catalog.npp(npp);
    BusinessException.require(
        file.getSize() > 0
            && file.getSize() <= 5 * 1024 * 1024
            && TYPES.contains(file.getContentType()),
        "Tệp không đúng loại/kích thước.");
    try {
      byte[] bytes = file.getBytes();
      BusinessException.require(
          magic(bytes, file.getContentType()), "Nội dung tệp không khớp MIME.");
      var a = new Attachment();
      a.nppId = npp;
      a.ownerId = actors.current().id();
      a.filename =
          Path.of(Objects.toString(file.getOriginalFilename(), "upload")).getFileName().toString();
      a.contentType = file.getContentType();
      a.sizeBytes = file.getSize();
      a.objectKey = "local-" + UUID.randomUUID();
      a.status = "READY_LOCAL_UNSCANNED";
      a.contentHash = sha(bytes);
      a.scanResult = "LOCAL_UNSCANNED";
      if (scanner.configured()) {
        BusinessException.require(scanner.clean(bytes), "Tệp bị dịch vụ quét từ chối.");
        a.status = "READY_SCANNED";
        a.scanResult = "CLAMAV_OK";
      }
      Path root = Path.of(".data/uploads").toAbsolutePath().normalize();
      Files.createDirectories(root);
      Path target = root.resolve(a.objectKey).normalize();
      if (!target.startsWith(root)) throw BusinessException.invalid("Đường dẫn không hợp lệ.");
      Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
      attachments.saveAndFlush(a);
      return Map.of(
          "id", a.id, "status", a.status, "note", "Chỉ dùng phát triển local, chưa quét malware.");
    } catch (IOException e) {
      throw new BusinessException("STORAGE_UNAVAILABLE", "Không lưu được tệp local.", 503);
    }
  }

  private boolean magic(byte[] b, String type) {
    if (b.length < 12) return false;
    return switch (type) {
      case "image/png" -> b[0] == (byte) 137 && b[1] == 80 && b[2] == 78 && b[3] == 71;
      case "image/jpeg" -> b[0] == (byte) 255 && b[1] == (byte) 216 && b[2] == (byte) 255;
      case "image/webp" ->
          new String(b, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
              && new String(b, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
      case "application/pdf" ->
          new String(b, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-");
      default -> false;
    };
  }

  public record FileData(String filename, String contentType, byte[] bytes) {}

  public FileData content(Long id) {
    var a = owned(id);
    BusinessException.require(
        a.status.equals("READY_SCANNED") || local && a.status.equals("READY_LOCAL_UNSCANNED"),
        "Tệp chưa qua kiểm tra bắt buộc.");
    try {
      if (!a.objectKey.startsWith("local-")) {
        try (var input =
            client()
                .getObject(GetObjectArgs.builder().bucket(bucket).object(a.objectKey).build())) {
          byte[] bytes = input.readNBytes(5 * 1024 * 1024 + 1);
          BusinessException.require(
              bytes.length == a.sizeBytes && sha(bytes).equals(a.contentHash),
              "Tệp đã thay đổi sau kiểm tra.");
          return new FileData(a.filename, a.contentType, bytes);
        } catch (BusinessException e) {
          throw e;
        } catch (Exception e) {
          throw new IOException(e);
        }
      }
      var root = Path.of(".data/uploads").toAbsolutePath().normalize();
      var target = root.resolve(a.objectKey).normalize();
      BusinessException.require(target.startsWith(root), "Đường dẫn không hợp lệ.");
      return new FileData(a.filename, a.contentType, Files.readAllBytes(target));
    } catch (IOException e) {
      throw BusinessException.missing();
    }
  }

  public void assertImage(Long id, Long npp) {
    var a = owned(id);
    BusinessException.require(
        a.nppId.equals(npp)
            && a.contentType.startsWith("image/")
            && Set.of("READY_LOCAL_UNSCANNED", "READY_SCANNED").contains(a.status),
        "Ảnh chưa sẵn sàng hoặc chưa qua kiểm tra bắt buộc.");
  }

  public void assertReady(Long id, Long npp) {
    var a = owned(id);
    BusinessException.require(
        a.nppId.equals(npp)
            && (a.status.equals("READY_SCANNED")
                || local && a.status.equals("READY_LOCAL_UNSCANNED")),
        "Tệp chưa sẵn sàng hoặc khác NPP.");
  }

  public String contentHash(Long id, Long npp) {
    assertReady(id, npp);
    return owned(id).contentHash;
  }

  private static String sha(byte[] bytes) {
    try {
      return java.util.HexFormat.of()
          .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
