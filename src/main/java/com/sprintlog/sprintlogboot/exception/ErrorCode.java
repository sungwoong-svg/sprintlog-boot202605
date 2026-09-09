package com.sprintlog.sprintlogboot.exception;

import org.springframework.http.HttpStatus;

// 에러 코드 체계 = 애플리케이션의 모든 "알려진 에러"를 한 곳으로 모은다.
public enum ErrorCode {

  // ── 활동(Activity) 도메인 — A로 시작 ──────────────────────────────
  ACTIVITY_NOT_FOUND     ("A001", HttpStatus.NOT_FOUND,   "활동을 찾을 수 없습니다."),
  INVALID_ACTIVITY_INPUT ("A002", HttpStatus.BAD_REQUEST, "활동 데이터가 올바르지 않습니다."),
  ACTIVITY_ARCHIVE_FAILED("A003", HttpStatus.CONFLICT,    "활동 보관 처리에 실패했습니다."),  // 예약(체크 예외 ActivityArchiveException 은 계층 밖)

  // ── 사용자(User) 도메인 — U로 시작 ──────────────────────────────
  EMAIL_ALREADY_EXIST("U001", HttpStatus.CONFLICT, "이미 사용중인 이메일 입니다."),
  USER_NOT_FOUND("U002", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

  // ── 공통(Common) — C로 시작 ──────────────────────────────────────
  INVALID_INPUT     ("C001", HttpStatus.BAD_REQUEST,       "입력값이 올바르지 않습니다."),
  RESOURCE_NOT_FOUND("C002", HttpStatus.NOT_FOUND,         "요청하신 경로를 찾을 수 없습니다."),
  ACCESS_DENIED     ("C003", HttpStatus.FORBIDDEN, "권한이 없습니다."),
  INTERNAL_ERROR    ("C999", HttpStatus.INTERNAL_SERVER_ERROR, "서버에서 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.");

  private final String code;             // "A001" — HTTP 상태와 독립적인 안정 식별자
  private final HttpStatus status;       // 이 에러의 HTTP 상태
  private final String defaultMessage;   // 상세 메시지를 안 주면 쓰는 기본 문구

  ErrorCode(String code, HttpStatus status, String defaultMessage) {
    this.code = code;
    this.status = status;
    this.defaultMessage = defaultMessage;
  }

  public String getCode() { return code; }
  public HttpStatus getStatus() { return status; }
  public String getDefaultMessage() { return defaultMessage; }

}
