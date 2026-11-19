package com.habitloop.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class PublishRetryJob {
    private static final Logger log = LoggerFactory.getLogger(PublishRetryJob.class);
    private final PartnerEventRepository events;
    private final EventPublisher publisher;
    PublishRetryJob(PartnerEventRepository events, EventPublisher publisher) { this.events = events; this.publisher = publisher; }

    @Scheduled(fixedDelay = 30000)
    void retryUnpublished() {
        for (PartnerEventEntity event : events.findTop100ByPublishedFalseAndPublishAttemptsLessThanOrderByReceivedAtAsc(10)) {
            try {
                publisher.publish(event);
                if (!event.published) event.published = true;
                events.save(event);
            } catch (RuntimeException ex) {
                event.publishAttempts++;
                events.save(event);
                if (event.publishAttempts >= 10) log.error("Partner event {} gave up after 10 publish attempts", event.id);
                else log.warn("Partner event {} publish retry {} failed", event.id, event.publishAttempts);
            }
        }
    }
}
