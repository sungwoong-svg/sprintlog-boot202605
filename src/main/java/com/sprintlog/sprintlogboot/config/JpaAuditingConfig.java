package com.sprintlog.sprintlogboot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/*
 원래 @EnableJpaAuditing은 메인 클래스에 붙어 있었다. 그러면 @WebMvcTest(웹 계층만 얇게
띄우는 슬라이스 테스트)가 메인 클래스를 설정 원천으로 잡으면서 JPA Auditing 초기화를 시도하는데,
웹 슬라이스엔 JPA(EntityManager·metamodel)가 없어 "JPA metamodel must not be empty"로 컨텍스트 로드가 깨진다.
이렇게 별도 @Configuration 으로 떼어 두면 @WebMvcTest 슬라이스는 이 config 를 로드하지 않으므로 문제가 사라지고,
전체 컨텍스트(@SpringBootTest·실제 실행)에서는 그대로 컴포넌트 스캔에 잡혀 Auditing 이 켜진다.
 */

@Configuration
// BaseEntity의 @CreateDate/@LastModifiedDate 자동 채움 기능을 켠다.
@EnableJpaAuditing // 이거 없으면 둘 다 null이 들어갑니다.
public class JpaAuditingConfig {

}
