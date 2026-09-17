package com.sprintlog.sprintlogboot.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration // 이 클래스는 Bean 정의 모음 설정 클래스임을 표시
@EnableConfigurationProperties({SprintLogProperties.class, JwtProperties.class})
public class AppConpig {

    // 반환 객체의 타입이 Bean 타입(Clock), 다른 곳에서 Clock clock로 주입 받으면 이 객체가 들어옴
    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }

}
