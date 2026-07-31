package com.sprintlog.sprintlogboot.service;

import com.sprintlog.sprintlogboot.config.S3Properties;
import com.sprintlog.sprintlogboot.exception.FileStorageException;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
@Slf4j
@ConditionalOnProperty(name = "sprintlog.storage", havingValue = "S3")
@RequiredArgsConstructor
public class S3Service implements FileStorage{

  // FileService와 동일한 허용 확장자 화이트리스
  private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
      ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg",   // 이미지
      ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",  // 문서
      ".txt", ".md", ".csv", ".json"                              // 텍스트
  );

  private final S3Client s3;
  private final S3Presigner presigner;
  private final S3Properties props;

  @Override
  public String saveFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("파일이 비어있습니다.");
    }

    String originalFilename = StringUtils.cleanPath(
        file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename());
    int dotIndex = originalFilename.lastIndexOf('.');
    String extension = (dotIndex >= 0) ? originalFilename.substring(dotIndex).toLowerCase() : "";

    if (!ALLOWED_EXTENSIONS.contains(extension)) {
      throw new IllegalArgumentException(
          "허용되지 않는 파일 형식입니다: '" + extension + "' (허용: " + ALLOWED_EXTENSIONS + ")");
    }

    String key = UUID.randomUUID().toString().replace("-", "") + extension;

    PutObjectRequest request = PutObjectRequest.builder()
        .bucket(props.getBucket())
        .key(key)
        .contentType(file.getContentType())
        .build();

    try {
      s3.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
      log.info("S3 업로드 완료: {} (원본: {}, 크기: {} bytes", key, originalFilename, file.getSize());
      return key;
    } catch (IOException e) {
      throw new FileStorageException("S3 업로드 실패: " + originalFilename, e);
    }
  }

  // 비공개 객체에 대한 presigner GET URL을 생성한다. 이 URL으 가진 사람은 정해진 시간 동안만
  // 그 객체 하나만 볼 수 있다. (버킷 자체는 비공개 유지)
  @Override
  public String getFileUrl(String storedName) {
    GetObjectPresignRequest presignedRequest = GetObjectPresignRequest.builder()
        .signatureDuration(Duration.ofMinutes(props.getPresignMinutes()))
        .getObjectRequest(GetObjectRequest.builder()
            .bucket(props.getBucket())
            .key(storedName)
            .build())
        .build();

    return presigner.presignGetObject(presignedRequest).url().toString();
  }

  // 응답 헤더에 Content-Disposition에 attachment; 를 작성하면 브라우저로 응답을 하는 것이 아닌
  // 다운로드로 응답하게 됩니다.
  // 클라이언트가 다운로드 요청을 보내면 S3에게 다운로드 가능한 URL을 받아서 응답하고 클라이언트는 해당 URL로 redirect해서 다운로드를 S3에게 직접 요청
  @Override
  public String getDownloadUrl(String storedName) {
    GetObjectPresignRequest presignedRequest = GetObjectPresignRequest.builder()
        .signatureDuration(Duration.ofMinutes(props.getPresignMinutes()))
        .getObjectRequest(GetObjectRequest.builder()
            .bucket(props.getBucket())
            .key(storedName)
            .responseContentDisposition("attachment; filename=\"" + storedName + "\"")
            .build())
        .build();

    return presigner.presignGetObject(presignedRequest).url().toString();
  }

  @Override
  public void deleteFile(String storedName) {
    if (storedName == null || storedName.isBlank()) {
      return;
    }
    s3.deleteObject(DeleteObjectRequest.builder()
        .bucket(props.getBucket())
        .key(storedName)
        .build());

    log.info("S3 객체 삭제: {}", storedName);

  }
}
