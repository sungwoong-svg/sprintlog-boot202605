package com.sprintlog.sprintlogboot.exception;

public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;

  // 기본 메시지(ErrorCode의 디폴트 메세지로)로 던진다.
  public BusinessException(ErrorCode errorCode) {
    super(errorCode.getDefaultMessage());
    this.errorCode = errorCode;
  }

  // 상세 메시지를 직접 주어 던진다.
  public BusinessException(ErrorCode errorCode, String detail) {
    super(detail);
    this.errorCode = errorCode;
  }

  // 예외 변환용 - 저수준 예외를 감싸 비즈니스 예외로 변환해서 던진다.
  public BusinessException(ErrorCode errorCode, String detail, Throwable cause) {
    super(detail, cause);
    this.errorCode = errorCode;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
