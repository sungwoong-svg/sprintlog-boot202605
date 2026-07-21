package com.sprintlog.sprintlogboot.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 커스텀 제약 어노테이션 - 활동 종류에 맞는 필수 필드가 잘 채워졌는가?
@Documented // JavaDoc 문서 생성 시 이 어노테이션 정보도 함께 표기되도록 한다.
@Target(ElementType.TYPE) // 객체 전체를 가져와서 여러 필드의 조합을 검증하기 때문에 TYPE으로 세팅
@Retention(RetentionPolicy.RUNTIME) // 프로그램이 실행 중일 때 동작해야 한다.
@Constraint(validatedBy = ActivityByTypeValidator.class) // 이 어노테이션이 붙었을 때 실제 검증을 수행할 검증 클래스는 누구인가?
public @interface ValidActivityByType {

  // Bean Validation 사양을 따르는 어노테이션은 반드시 아래 3가지 속성을 포함해야 합니다.

  // 검증이 실패했을 때 반환할 기본 에러 메시지를 정의
  String message() default "활동 종류에 필요한 필드가 비어 있습니다.";

  // 검증 그룹을 지정할 때 사용하는 속성
  Class<?>[] groups() default {};

  // 심각도, 메타데이터 등 클라이언트가 필요로 하는 부가 정보를 검증 객체에 담아 전달할 때 사용(실무에서는 거의 사용되지 않음)
  Class<? extends Payload>[] payload() default {};

}
