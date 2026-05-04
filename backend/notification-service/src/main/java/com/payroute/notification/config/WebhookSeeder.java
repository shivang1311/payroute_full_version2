package com.payroute.notification.config;

import com.payroute.notification.entity.WebhookEndpoint;
import com.payroute.notification.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Auto-seeds a "global fan-out" webhook endpoint on startup so payment events
 * flow without any manual UI configuration. Controlled via
 * {@code payroute.webhook.seed.*} in application.yml.
 *
 * <p>Idempotent: only creates the row if none exists with the same (userId=0, url).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookSeeder implements CommandLineRunner {

    private final WebhookEndpointRepository endpointRepository;

    @Value("${payroute.webhook.seed.enabled:false}")
    private boolean enabled;

    @Value("${payroute.webhook.seed.url:}")
    private String seedUrl;

    @Value("${payroute.webhook.seed.name:Default (auto-seeded)}")
    private String seedName;

    @Value("${payroute.webhook.seed.user-id:0}")
    private Long seedUserId;

    @Value("${payroute.webhook.seed.events:*}")
    private String seedEvents;

    @Override
    public void run(String... args) {
        if (!enabled) {
            log.debug("Webhook auto-seed disabled");
            return;
        }
        if (seedUrl == null || seedUrl.isBlank()) {
            log.warn("Webhook auto-seed enabled but seed URL is blank — skipping");
            return;
        }
        endpointRepository.findFirstByUserIdAndUrl(seedUserId, seedUrl).ifPresentOrElse(
                existing -> log.info("Webhook auto-seed: endpoint already present (id={} url={})",
                        existing.getId(), existing.getUrl()),
                () -> {
                    WebhookEndpoint ep = WebhookEndpoint.builder()
                            .userId(seedUserId)
                            .name(seedName)
                            .url(seedUrl)
                            .secret(generateSecret())
                            .events(seedEvents == null || seedEvents.isBlank() ? "*" : seedEvents.trim())
                            .active(Boolean.TRUE)
                            .build();
                    ep = endpointRepository.save(ep);
                    log.info("Webhook auto-seed: created global fan-out endpoint id={} url={} events={}",
                            ep.getId(), ep.getUrl(), ep.getEvents());
                });
    }

    private String generateSecret() {
        byte[] buf = new byte[32];
        new SecureRandom().nextBytes(buf);
        StringBuilder sb = new StringBuilder("whsec_");
        for (byte b : buf) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
