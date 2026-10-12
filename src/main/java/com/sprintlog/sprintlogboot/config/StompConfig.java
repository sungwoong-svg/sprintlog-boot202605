package com.sprintlog.sprintlogboot.config;

import com.sprintlog.sprintlogboot.websocket.StompAuthChannelInterceptor;
import com.sprintlog.sprintlogboot.websocket.StompErrorHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class StompConfig implements WebSocketMessageBrokerConfigurer {

  private final StompAuthChannelInterceptor authInterceptor;
  private final StompErrorHandler errorHandler;

  // 하트비트를 보내려면 시계(스케줄러)가 필요하고, STOMP 설정이 이미 하나 만들어 두므로 그걸 갖다 쓴다.
  private TaskScheduler messageBrokerTaskScheduler;

  @Autowired
  public void setMessageBrokerTaskScheduler(
      @Lazy TaskScheduler messageBrokerTaskScheduler) {
    this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws");
    registry.setErrorHandler(errorHandler);
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    registration
        .setMessageSizeLimit(8 * 1024) // 받는 메시지 하나 최대 8KB
        .setSendBufferSizeLimit(512 * 1024) // 느린 사람에게 쌓아 둘 수 있는 양
        .setSendTimeLimit(10_000); // 한 사람에게 보내는데 10초 넘게 걸리면 그 연결을 끊겠다.
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic", "/queue")
        .setHeartbeatValue(new long[]{10_000, 10_000}) // 서버가 보내는 간격 10초, 브라우저에게 기대하는 간격 10초
        .setTaskScheduler(messageBrokerTaskScheduler);
    registry.setApplicationDestinationPrefixes("/app"); // /app으로 시작하는 건 브로커가 아니라 우리 컨트롤러가 처리한다.
    registry.setUserDestinationPrefix("/user");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(authInterceptor);
  }
}
