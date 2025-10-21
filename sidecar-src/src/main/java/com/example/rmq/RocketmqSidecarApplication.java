
package com.example.rmq;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RocketmqSidecarApplication {
    public static void main(String[] args) {
        // 仅监听本机，避免暴露到外网
        System.setProperty("server.address","127.0.0.1");
        SpringApplication.run(RocketmqSidecarApplication.class, args);
    }
}
