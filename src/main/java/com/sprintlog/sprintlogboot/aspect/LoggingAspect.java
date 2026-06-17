package com.sprintlog.sprintlogboot.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

    // 1. Pointcut (어디서?)
    // execution([수식어] 리턴타입 [클래스경로.]메서드이름(파라미터) [예외]) - []는 생략 가능한 문법
    // @Pointcut("execution(* com.codeit.springwebbasic.member.controller.MemberController.*(..))")
    // 모든 접근 제한자 허용, 모든 리턴타입 허용, MemberController 안에 있는 모든 메서드를 대상(매개값은 모든 파라미터)
    // @Pointcut("execution(* com.codeit.springwebbasic..*.*(..))")
    // ..: 0개 이상의 하위 패키지를 의미 -> 모든 하위 패키지를 전부 지목하고 싶을 때


//    @Pointcut("execution(* com.codeit.springwebbasic.member.controller.MemberController.*(..))")
//    private void allControllerMethods() {
//        // 위에서 지정한 (어디에?) 라는 메서드 위치에 사전에 지정해야 할 여러 설정, 사전 작업 등을 명시합니다.
//        // @Pointcut을 생략하고, @Around에 바로 execution을 작성해도 됩니다.
//        System.out.println("allControllerMethods 호출!");
//    }

    // '부가 기능을 어디에 적용할 것인가'에 대한 대상을 지정합니다.
    // *: 메서드의 반환 타입은 무엇이든 상관 없다.
    // com.sprintlog.sprintlogboot.controller..: 이 패키지 및 그 하위 모든 패키지(..)에 있는 클래스를 대상으로 한다.
    // *(..) : 메서드 이름은 무엇이든 상관 없고(*), 매개변수의  개수나 타입도 무엇이든 상관없다.(..)
    @Pointcut("execution(* com.sprintlog.sprintlogboot.controller..*(..))")
    public void controllerLayer() {}

    // 대상 메서드가 실행되기 전, 후 전체를 가로채서 제어할 수 있는 어노테이션
    @Around("controllerLayer()")
    public Object logAndMeasure(ProceedingJoinPoint joinPoint) throws Throwable {
        // ProceedingJoinPoint: AOP가 가로챈 실제 실행될 메서드의 정보를 담고 있는 객체
        String method = joinPoint.getSignature().toShortString(); // 메서드 이름 추출
        Object[] args = joinPoint.getArgs(); // 메서드로 전달된 매개값(인자) 추출

        long start = System.currentTimeMillis();

        log.info("요청 시작: {}, 인자={}", method, Arrays.toString(args));

        Object result = joinPoint.proceed();// 원래 메서드 실행

        long end = System.currentTimeMillis();

        log.info("요청 완료: {} ({}ms)", method, end - start);

        return result; // 원본 메서드가 반환하는 값을 그대로 클라이언트에게 리턴.

    }

    // @Before: 원본 메서드가 실행되기 직전까지만 딱 실행 됨.
    // joinPoint.proceed()를 따로 호출하지 않습니다.

    // @AfterReturning: 메서드 정상 호출 종료 이후 실행할 내용

    // 이 두개의 기능을 한꺼번에 아우를 수 있는 어노테이션이 바로 @Around

    // throwing = "ex" : 메서드가 던진 예외를 ex 파라미터로 받는다. (예외가 날 때만 실행됨)
    @AfterThrowing(pointcut = "controllerLayer()", throwing = "ex")
    public void afterServiceThrows(JoinPoint joinPoint, Throwable ex) {
        log.warn("[@AfterThrowing] {} 예외 발생: {}",
                joinPoint.getSignature().toShortString(), ex.getMessage());

    }

}
