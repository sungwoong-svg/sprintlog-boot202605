package com.sprintlog.sprintlogboot.service;

import com.sprintlog.sprintlogboot.exception.FileStorageException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class FileService {

  /**
   * 허용 확장자 화이트리스트. blacklist(금지 목록)는 우회가 끝없으므로,
   * *허용 목록만* 명시하는 것이 업로드 보안의 표준이다(서버 실행 파일 차단 → RCE 방지).
   */
  private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
      ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg",   // 이미지
      ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",  // 문서
      ".txt", ".md", ".csv", ".json"                              // 텍스트
  );

  private final Path uploadPath;

  public FileService(@Value("${sprintlog.file-directory}") String uploadDir) {
    // 저장 경로를 절대 경로로 정규화 — 이후 모든 traversal 검증의 기준점.
    this.uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
    try {
      // 이미 있으면 무해, 없으면 만든다. 실패 시 *시작 시점에* 명시적으로 터뜨린다.
      Files.createDirectories(uploadPath);
      log.info("업로드 디렉터리 준비 완료: {}", uploadPath);
    } catch (IOException e) {
      throw new FileStorageException("업로드 디렉터리 생성 실패: " + uploadPath, e);
    }
  }

  /**
   * {@link MultipartFile} 을 디스크에 저장하고, *저장된(UUID) 파일명* 을 돌려준다.
   * 이 파일명을 엔티티에 보관해 두면(activity.attachFile(name)), 나중에 다운로드할 때 찾을 수 있다.
   */
  public String saveFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("파일이 비어있습니다.");
    }

    // 원본 파일명 정규화(null·traversal 안전) 후 확장자만 추출.
    String originalFilename = StringUtils.cleanPath(
        file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename());
    int dotIndex = originalFilename.lastIndexOf('.');
    String extension = (dotIndex >= 0) ? originalFilename.substring(dotIndex).toLowerCase() : "";

    if (!ALLOWED_EXTENSIONS.contains(extension)) {
      throw new IllegalArgumentException(
          "허용되지 않는 파일 형식입니다: '" + extension + "' (허용: " + ALLOWED_EXTENSIONS + ")");
    }

    // 충돌·추측 불가능한 UUID 파일명 + 원본 확장자.
    String savedFileName = UUID.randomUUID().toString().replace("-", "") + extension;
    Path targetPath = uploadPath.resolve(savedFileName).normalize();

    // 방어적 검증 — 저장 경로가 업로드 디렉터리 밖이면 거부(defense in depth).
    if (!targetPath.startsWith(uploadPath)) {
      throw new FileStorageException("저장 경로가 업로드 디렉터리 외부입니다: " + targetPath);
    }

    try {
      file.transferTo(targetPath);
      log.info("파일 저장 완료: {} (원본: {}, 크기: {} bytes)",
          savedFileName, originalFilename, file.getSize());
      return savedFileName;
    } catch (IOException e) {
      throw new FileStorageException("파일 저장 실패: " + originalFilename, e);
    }
  }

  /**
   * 저장된 파일을 *스트림 기반 {@link Resource}* 로 읽어 돌려준다(컨트롤러가 바이너리로 응답).
   * 전체를 byte[] 로 메모리에 올리지 않으므로 큰 파일에도 안전하다. 없으면 {@code null} 을 반환한다.
   */
  public Resource loadAsResource(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return null;
    }
    Path requested = uploadPath.resolve(fileName).normalize();
    if (!requested.startsWith(uploadPath)) {
      log.warn("다운로드 요청 거부 — 업로드 디렉터리 외부 경로: {}", fileName);
      return null;
    }
    if (!Files.exists(requested) || Files.isDirectory(requested)) {
      return null;
    }
    try {
      return new UrlResource(requested.toUri());
    } catch (MalformedURLException e) {
      throw new FileStorageException("파일 경로가 잘못되었습니다: " + fileName, e);
    }
  }

  /**
   * 파일의 콘텐츠 타입을 추정한다. 알 수 없으면 {@code application/octet-stream}(바이너리 기본).
   */
  public String probeContentType(String fileName) {
    try {
      Path requested = uploadPath.resolve(fileName).normalize();
      String contentType = Files.probeContentType(requested);
      return (contentType == null) ? "application/octet-stream" : contentType;
    } catch (IOException e) {
      return "application/octet-stream";
    }
  }

  /**
   * 저장된 파일 삭제. null·빈 이름은 조용히 무시. (활동을 지울 때 첨부도 함께 정리하는 용도)
   */
  public void deleteFile(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return;
    }
    Path target = uploadPath.resolve(fileName).normalize();
    if (!target.startsWith(uploadPath)) {
      log.warn("삭제 요청 거부 — 업로드 디렉터리 외부 경로: {}", fileName);
      return;
    }
    try {
      // 한 시스템 콜로 검사·삭제(atomic) — exists 후 delete 의 경쟁 조건을 피한다.
      if (Files.deleteIfExists(target)) {
        log.info("파일 삭제: {}", fileName);
      }
    } catch (IOException e) {
      log.error("파일 삭제 실패: {}", fileName, e);
    }
  }
}