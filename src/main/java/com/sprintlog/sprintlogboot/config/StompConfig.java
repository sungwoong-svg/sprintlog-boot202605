package com.sprintlog.sprintlogboot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class StompConfig implements WebSocketMessageBrokerConfigurer {

  // 하트비트를 보내려면 시계(스케줄러)가 필요하고, STOMP 설정이 이미 하나 만들어 두므로 그걸 갖다 쓴다.
  private TaskScheduler messageBrokerTaskScheduler;

  public StompConfig(@Lazy TaskScheduler messageBrokerTaskScheduler) {
    this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws");
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic", "/queue")
        .setHeartbeatValue(new long[]{10_000, 10_000}) // 서버가 보내는 간격 10초, 브라우저에게 기대하는 간격 10초
        .setTaskScheduler(messageBrokerTaskScheduler);
    registry.setApplicationDestinationPrefixes("/app"); // /app으로 시작하는 건 브로커가 아니라 우리 컨트롤러가 처리한다.
    registry.setUserDestinationPrefix("/user");
  }
}
