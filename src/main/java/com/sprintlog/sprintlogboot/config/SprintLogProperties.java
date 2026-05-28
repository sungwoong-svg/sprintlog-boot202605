package com.sprintlog.sprintlogboot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

// 관련된 키 묶음을 타입 안전한 객체로 권장 패턴
@ConfigurationProperties(prefix = "sprintlog")
@Getter @Setter
public class SprintLogProperties {

    private String welcomeMessage;
    private SampleData sampleData = new SampleData();

    @Getter @Setter
    public static class SampleData {
        private boolean enabled;
        private int count;

    }
}
