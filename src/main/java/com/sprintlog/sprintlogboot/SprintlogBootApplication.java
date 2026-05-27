package com.sprintlog.sprintlogboot;

import com.sprintlog.sprintlogboot.domain.*;
import com.sprintlog.sprintlogboot.lifecycle.ImportBatch;
import com.sprintlog.sprintlogboot.printer.ActivityPrinter;
import com.sprintlog.sprintlogboot.repository.ActivityRepository;
import com.sprintlog.sprintlogboot.service.ActivityDashboard;
import com.sprintlog.sprintlogboot.service.ActivityReportService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

@SpringBootApplication
public class SprintlogBootApplication {

    public static void main(String[] args) {
        SpringApplication.run(SprintlogBootApplication.class, args);


    }

    // 메서드가 리턴하는 객체를 Bean으로 등록하겠다.
    // 내가 직접 만든 클래스는 직접 @Component 등을 사용해서 빈 등록하면 되지만
    // 라이브러리 등에서 제공하는 객체는 메서드 안에서 객체를 생성한 후에 리턴되는 객체를 Bean으로 등록하기 위해 @Bean을 사용.

    // CommandLineRunner: Spring Boot가 구동이 완료되고, 딱 한번 실행되는 코드를 정의할 때 사용하는 인터페이스.
    // 주로 초기 데이터 적재나 설정 확인용으로 쓰입니다.
    @Bean
    public CommandLineRunner demonstrateBeans(
            ApplicationContext context,
            ActivityRepository repository,
            ActivityDashboard dashboard,
            ActivityReportService reportService,
            @Qualifier("compact") ActivityPrinter defaultPrinter,
            List<ActivityPrinter> allPrinters,
            Map<String, ActivityPrinter> printersByName,
            @Value("${sprintlog.welcome-message}") String welcomeMessage) {

        // CommandLineRunner의 구현체를 익명 클래스 람다식으로 작성. run 메서드를 구현.
        return args -> {
            System.out.println();
            System.out.println("==================================================");
            System.out.println("  SprintLog Boot — 외부 설정 시연");
            System.out.println("==================================================");

            System.out.println();
            System.out.println("── Repository 상태 (Profile 별 Initializer 가 결정) ──");
            System.out.println("  활동 수: " + repository.count() + "개");
            for (LearningActivity activity : repository.findAll()) {
                defaultPrinter.print(activity);
            }

            System.out.println();
            System.out.println("==================================================");
            System.out.println("  Bean 시연 완료 — 톰캣은 계속 8080 에서 동작 중");
            System.out.println("==================================================");
            System.out.println();
        };
    }

}
