package com.payroute.routing.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Configuration
public class FeignConfig {

    private static final List<String> FORWARDED_HEADERS = List.of(
            "X-User-Id",
            "X-Username",
            "X-User-Role",
            "X-Correlation-Id"
    );

    @Bean
    public RequestInterceptor requestHeaderInterceptor() {
        return (RequestTemplate template) -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                for (String header : FORWARDED_HEADERS) {
                    String value = request.getHeader(header);
                    if (value != null) {
                        template.header(header, value);
                    }
                }
            }
        };
    }
}
