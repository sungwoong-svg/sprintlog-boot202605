package com.sprintlog.sprintlogboot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "sprintlog.s3")
public class S3Properties {

  /** 버킷 이름. */
  private String bucket = "sprintlog-uploads-7569";

  /** 리전(예: ap-northeast-2 서울). */
  private String region = "ap-northeast-2";

  /** 커스텀 엔드포인트. 비어 있으면 실제 AWS, 값이 있으면 로컬 목(S3Mock/LocalStack). */
  private String endpoint;

  /** presigned URL 유효 시간(분). 짧을수록 안전. */
  private int presignMinutes = 5;

}
