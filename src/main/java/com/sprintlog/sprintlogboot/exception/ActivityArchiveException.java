package com.sprintlog.sprintlogboot.exception;

// 이 예외는 애초에 예외 처리를 강제하기 위한 타입이라 BusinessException을 상속하기엔 적합하지 않겠다.
public class ActivityArchiveException extends Exception {

  public ActivityArchiveException(String message) {
    super(message);
  }
}
