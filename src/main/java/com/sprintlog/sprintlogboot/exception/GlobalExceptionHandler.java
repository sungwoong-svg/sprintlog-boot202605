package com.sprintlog.sprintlogboot.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ActivityNotFoundException.class)
    public ProblemDetail handleNotFound(ActivityNotFoundException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        pd.setTitle("활동을 찾을 수 없습니다.");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        // 1. 오류 결과를 담을 Map을 생성합니다. (key: 필드명, value: 에러메시지)
        Map<String, String> errors =  new HashMap<>();

        /*
        // BindingResult: 오류 결과 보고서
        BindingResult bindingResult = e.getBindingResult();

        // BindingResult에서 @Valid에 실패한 필드 목록을 불러옵니다.
        List<FieldError> fieldErrors = bindingResult.getFieldErrors();
        for (FieldError error : fieldErrors) {
            String field = error.getField();
            String message = error.getDefaultMessage();
            errors.put(field, message);
        }
        */

        e.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        ProblemDetail pd
                = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "요청 본문의 일부 필드가 유효하지 않습니다.");

        pd.setTitle("입력 검증 실패");
        pd.setProperty("timestamp", Instant.now());
        pd.setProperty("errors", errors);
        return pd;
    }

    // 400 — 도메인 규칙 위반 (예: 학습 시간 0분, 빈 제목 등 도메인 생성자가 던짐)
    @ExceptionHandler(InvalidActivityException.class)
    public ProblemDetail handleInvalidActivity(InvalidActivityException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        pd.setTitle("잘못된 활동 데이터");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    // 400 — JSON 자체가 깨졌거나 enum 에 없는 값 등, 요청 본문을 읽지 못함
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleNotReadable(HttpMessageNotReadableException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "요청 본문(JSON)을 읽을 수 없습니다. 형식이나 값을 확인하세요.");
        pd.setTitle("요청 본문 오류");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    // 500 — 그 밖의 예상 못 한 오류. 원본 메시지는 로그에만, 클라이언트엔 안전한 문구만
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleException(Exception e) {
        log.error("예상치 못한 서버 오류", e);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "서버에서 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        pd.setTitle("서버 오류");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
