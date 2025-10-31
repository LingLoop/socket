package song.lingloop;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import song.lingloop.netty.WebSocketServer;

import java.net.http.WebSocket;

@RequiredArgsConstructor
@SpringBootApplication
public class LingloopApplication {
    private final WebSocketServer server;

    public static void main(String[] args) {
        SpringApplication.run(LingloopApplication.class, args);
    }

    @Bean
    public ApplicationListener<ApplicationReadyEvent> readyEventApplicationListener() {
        return applicationReadyEvent -> server.start();
    }

}
