package com.sprintlog.sprintlogboot;

import com.sprintlog.sprintlogboot.domain.LectureLog;
import com.sprintlog.sprintlogboot.domain.PracticeLog;
import com.sprintlog.sprintlogboot.domain.ReadingLog;
import com.sprintlog.sprintlogboot.domain.Visibility;
import com.sprintlog.sprintlogboot.lifecycle.ImportBatch;
import com.sprintlog.sprintlogboot.printer.ActivityPrinter;
import com.sprintlog.sprintlogboot.repository.ActivityRepository;
import com.sprintlog.sprintlogboot.service.ActivityDashboard;
import com.sprintlog.sprintlogboot.service.ActivityReportService;
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
            List<ActivityPrinter> allPrinters,
            Map<String, ActivityPrinter> printersByName) {

        // CommandLineRunner의 구현체를 익명 클래스 람다식으로 작성. run 메서드를 구현.
        return args -> {
            System.out.println();
            System.out.println("==================================================");
            System.out.println("  SprintLog Boot — Bean Scope + 생명주기 시연");
            System.out.println("==================================================");

            // 1. DataInitializer 의 @PostConstruct 가 이미 실행됐는지 확인
            //    (이 메서드 실행 시점엔 이미 샘플 데이터가 있어야 함)
            System.out.println();
            System.out.println("── 1. CommandLineRunner 시작 시점의 Repository 상태 ──");
            System.out.println("  활동 수: " + repository.count() + "개 (← DataInitializer 가 미리 적재)");

            // 2. Singleton 검증 — 같은 ActivityRepository 를 두 번 꺼내면 동일 인스턴스인가?
            System.out.println();
            System.out.println("── 2. Singleton 검증 — ActivityRepository ──");
            ActivityRepository repo1 = context.getBean(ActivityRepository.class);
            ActivityRepository repo2 = context.getBean(ActivityRepository.class);
            System.out.println("  repo1 == repo2 ? " + (repo1 == repo2));
            System.out.println("  repo1.hashCode(): " + repo1.hashCode());
            System.out.println("  repo2.hashCode(): " + repo2.hashCode());
            System.out.println("  Parameter repo: " + repository.hashCode());

            // 3. Prototype 검증 — ImportBatch 를 두 번 꺼내면 서로 다른 인스턴스일까?
            System.out.println();
            System.out.println("── 3. Prototype 검증 — ImportBatch ──");
            ImportBatch batch1 = context.getBean(ImportBatch.class);
            // 살짝 시간 차이를 두고 두 번째 인스턴스 생성
            Thread.sleep(10);
            ImportBatch batch2 = context.getBean(ImportBatch.class);
            System.out.println("  batch1: " + batch1);
            System.out.println("  batch2: " + batch2);
            System.out.println("  batch1 == batch2 ? " + (batch1 == batch2));


            System.out.println();
            System.out.println("==================================================");
            System.out.println("  Bean 시연 완료 — 톰캣은 계속 8080 에서 동작 중");
            System.out.println("==================================================");
            System.out.println();
        };
    }

}
