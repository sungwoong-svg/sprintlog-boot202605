package com.sprintlog.sprintlogboot.printer;


import com.sprintlog.sprintlogboot.domain.LearningActivity;
import org.springframework.stereotype.Component;

@Component("console") // 이름을 안붙이는게 일반적
// @Primary // ActivityPrinter 타입의 객체에서는 이거 붙인게 기본값
public class ConsoleActivityPrinter implements ActivityPrinter {

    @Override
    public void print(LearningActivity activity) {
        System.out.println(
                "[" + activity.getActivityType() + "]"
                        + " #" + activity.getId()
                        + " " + activity.getTitle()
                        + " - " + activity.getMinutes() + "분"
                        + " - " + activity.getDetailText()
                        + " - " + activity.getVisibilityText() + "🙏"
        );
    }

}
