package com.sprintlog.sprintlogboot.config;

import com.sprintlog.sprintlogboot.websocket.EchoWebSocketHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@ConditionalOnProperty(name = "sprintlog.ws.echo-enabled", havingValue = "true")
public class WebSocketEchoConfig implements WebSocketConfigurer {

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(new EchoWebSocketHandler(), "/ws/echo");
  }
}
