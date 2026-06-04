package com.bangku.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}

@Configuration
@Slf4j
class GatewayConfig {

    @Value("${account.service.url:http://account-service:8081}")
    private String accountServiceUrl;

    @Value("${transaction.service.url:http://transaction-service:8082}")
    private String transactionServiceUrl;

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
    @Bean
    public RouterFunction<ServerResponse> routes(WebClient.Builder wcb) {
        return RouterFunctions.route()
                .GET("/health", req -> ServerResponse.ok().bodyValue(Map.of(
                        "service", "bangku-api-gateway",
                        "status",  "UP",
                        "time",    Instant.now().toString()
                )))
                .GET("/api/v1/accounts/**",      req -> proxy(wcb, req, accountServiceUrl))
                .POST("/api/v1/accounts/**",     req -> proxy(wcb, req, accountServiceUrl))
                .PUT("/api/v1/accounts/**",      req -> proxy(wcb, req, accountServiceUrl))
                .DELETE("/api/v1/accounts/**",   req -> proxy(wcb, req, accountServiceUrl))
                .GET("/api/v1/transactions/**",  req -> proxy(wcb, req, transactionServiceUrl))
                .POST("/api/v1/transactions/**", req -> proxy(wcb, req, transactionServiceUrl))
                .build();
    }

    private Mono<ServerResponse> proxy(WebClient.Builder wcb, ServerRequest req, String baseUrl) {
        String path  = req.uri().getRawPath();
        String query = req.uri().getRawQuery();
        String target = baseUrl + path + (query != null ? "?" + query : "");

        log.debug("Gateway → {} {}", req.method(), target);

        return wcb.build()
                .method(req.method())
                .uri(URI.create(target))
                .headers(h -> h.addAll(req.headers().asHttpHeaders()))
                .body(req.bodyToMono(String.class), String.class)
                .retrieve()
                .toEntity(String.class)
                .flatMap(resp -> ServerResponse.status(resp.getStatusCode())
                        .headers(h -> {
                            if (resp.getHeaders().getContentType() != null)
                                h.setContentType(resp.getHeaders().getContentType());
                        })
                        .bodyValue(resp.getBody() != null ? resp.getBody() : ""));
    }
}
