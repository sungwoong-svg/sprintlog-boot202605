package com.sprintlog.sprintlogboot.lifecycle;

import com.sprintlog.sprintlogboot.config.SprintLogProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProdDataInitializer {

    private final SprintLogProperties properties;

    public ProdDataInitializer(SprintLogProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void notice() {
        System.out.println("[lifecycle][prod] @PostConstruct — " + properties.getWelcomeMessage());
        System.out.println("[lifecycle][prod] 운영 환경: 샘플 데이터 적재 안 함. 실제 데이터 사용.");
    }

}
