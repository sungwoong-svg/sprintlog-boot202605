package com.sprintlog.sprintlogboot.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorage {

  /** {@link MultipartFile} 을 저장하고, 저장된 이름(key) 을 돌려준다(엔티티에 보관). */
  String saveFile(MultipartFile file);

  /**
   * 저장된 파일을 브라우저가 볼 수 있는 URL 로 돌려준다.
   *  - 로컬: 우리 앱의 다운로드 엔드포인트({@code /api/files/{name}})
   *  - S3  : 비공개 객체에 대한 presigned URL(정해진 시간만 유효)
   */
  String getFileUrl(String storedName);

  /**
   * 저장된 파일을 브라우저가 내려받게(저장) 하는 URL 을 돌려준다. {@link #getFileUrl} 과 거의 같지만,
   * "보기(inline)" 가 아니라 "다운로드(attachment)" 로 응답하게 한다.
   *  - 로컬: 다운로드 헤더가 붙는 URL({@code /api/files/{name}?download=1})
   *  - S3  : {@code response-content-disposition=attachment} 가 담긴 presigned URL
   */
  String getDownloadUrl(String storedName);

  /** 저장된 파일 삭제. null·빈 이름은 무시. */
  void deleteFile(String storedName);

}
