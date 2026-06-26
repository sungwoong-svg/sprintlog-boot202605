package com.sprintlog.sprintlogboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// BaseEntity의 @CreateDate/@LastModifiedDate 자동 채움 기능을 켠다.
@EnableJpaAuditing // 이거 없으면 둘 다 null이 들어갑니다.
@SpringBootApplication
public class SprintlogBootApplication {

    public static void main(String[] args) {
        SpringApplication.run(SprintlogBootApplication.class, args);
    }


}
