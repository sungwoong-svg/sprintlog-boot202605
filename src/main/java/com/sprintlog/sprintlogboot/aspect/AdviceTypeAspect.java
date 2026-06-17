package com.sprintlog.sprintlogboot.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class AdviceTypeAspect {

    // 재사용 포인트컷: service 패키지(하위 포함)의 모든 메서드
    @Pointcut("execution(* com.sprintlog.sprintlogboot.service..*(..))")
    public void serviceLayer() {}

    @Before("serviceLayer()")
    public void beforeService(JoinPoint joinPoint) {
        log.info("[@Before] {} 호출 직전", joinPoint.getSignature().toShortString());
    }

    // returning = "result" : 메서드의 반환값을 result 파라미터로 받는다.
    @AfterReturning(pointcut = "serviceLayer()", returning = "result")
    public void afterServiceReturns(JoinPoint joinPoint, Object result) {
        log.info("[@AfterReturning] {} 정상 반환 (반환타입={})",
                joinPoint.getSignature().toShortString(),
                result == null ? "null" : result.getClass().getSimpleName());
    }

    // throwing = "ex" : 메서드가 던진 예외를 ex 파라미터로 받는다. (예외가 날 때만 실행됨)
    @AfterThrowing(pointcut = "serviceLayer()", throwing = "ex")
    public void afterServiceThrows(JoinPoint joinPoint, Throwable ex) {
        log.warn("[@AfterThrowing] {} 예외 발생: {}",
                joinPoint.getSignature().toShortString(), ex.getMessage());

    }
}
