package com.payroute.party.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig {

    private static final String[] FORWARDED_HEADERS = {
            "X-User-Id",
            "X-Username",
            "X-User-Role",
            "X-Party-Id",
            "X-Correlation-Id"
    };

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
