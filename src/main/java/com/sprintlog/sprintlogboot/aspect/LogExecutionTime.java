package com.sprintlog.sprintlogboot.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// SOURCE(소스 코드 내에서만 사용되고 사라지는 것 ex. Override), CLASS(바이트 코드 전환 후 실행 후 로딩되지 않음), RUNTIME(실행 중에도 남아있어야함)
@Retention(RetentionPolicy.RUNTIME) // AOP는 app이 실행되는 도중에 동적으로 특정 메서드를 가로 채서 기능을 추가하기 때문에 RUNTIME으로 선언
@Target(ElementType.METHOD) // 어노테이션을 붙일 수 있는 메서드다.
public @interface LogExecutionTime {
}
