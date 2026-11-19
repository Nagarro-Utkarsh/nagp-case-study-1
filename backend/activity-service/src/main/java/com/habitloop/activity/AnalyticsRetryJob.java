package com.habitloop.activity;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class AnalyticsRetryJob {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsRetryJob.class);
    private final ActivityLogRepository logs;
    private final RestClient client;
    private final String analyticsUrl;
    private final String internalKey;

    AnalyticsRetryJob(ActivityLogRepository logs, RestClient.Builder builder,
                      @Value("${ANALYTICS_URL:http://localhost:8085}") String analyticsUrl,
                      @Value("${INTERNAL_KEY:local-internal-key}") String internalKey) {
        this.logs = logs; this.client = builder.build(); this.analyticsUrl = analyticsUrl; this.internalKey = internalKey;
    }

    @Scheduled(fixedDelay = 30000)
    void retryUnsynced() {
        for (ActivityLog entry : logs.findTop100ByAnalyticsSyncedFalseOrderByLoggedAtAsc()) {
            try {
                client.post().uri(analyticsUrl + "/internal/analytics/activity").header("X-Internal-Key", internalKey)
                        .body(Map.of("logId", entry.id, "tenantId", entry.tenantId, "userId", entry.userId,
                                "habitId", entry.habitId, "loggedAt", entry.loggedAt)).retrieve().toBodilessEntity();
                entry.analyticsSynced = true;
                logs.save(entry);
            } catch (RuntimeException ex) {
                log.warn("Analytics retry failed for activity log {}", entry.id);
            }
        }
    }
}
