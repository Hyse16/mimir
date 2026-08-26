package com.mimir.blog;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.mimir.blog.ImageAnalysisApiModels.AiJobProgressEventResponse;

@Service
class ImageAnalysisJobEventStreamService {

    private static final long HEARTBEAT_INTERVAL_MILLIS = 15_000;

    private final ImageAnalysisJobService service;
    private final TaskScheduler scheduler;
    private final long pollIntervalMillis;
    private final long timeoutMillis;

    ImageAnalysisJobEventStreamService(
            ImageAnalysisJobService service,
            @Qualifier("imageAnalysisEventScheduler") TaskScheduler scheduler,
            @Value("${mimir.ai.event-poll-interval:500ms}") Duration pollInterval,
            @Value("${mimir.ai.event-stream-timeout:10m}") Duration timeout) {
        if (pollInterval.isZero() || pollInterval.isNegative()) {
            throw new IllegalArgumentException("AI event poll interval must be positive.");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("AI event stream timeout must be positive.");
        }
        this.service = service;
        this.scheduler = scheduler;
        this.pollIntervalMillis = pollInterval.toMillis();
        this.timeoutMillis = timeout.toMillis();
    }

    SseEmitter stream(UUID jobId, long lastEventId) {
        service.eventsAfter(jobId, lastEventId);
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        AtomicLong cursor = new AtomicLong(lastEventId);
        AtomicLong lastHeartbeat = new AtomicLong(System.currentTimeMillis());
        AtomicBoolean closed = new AtomicBoolean();
        AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>();

        Runnable close = () -> {
            if (closed.compareAndSet(false, true)) {
                ScheduledFuture<?> scheduled = future.get();
                if (scheduled != null) {
                    scheduled.cancel(false);
                }
            }
        };
        Runnable poll = () -> {
            if (closed.get()) {
                return;
            }
            try {
                List<AiJobProgressEventResponse> events = service.eventsAfter(jobId, cursor.get());
                for (AiJobProgressEventResponse event : events) {
                    emitter.send(SseEmitter.event()
                            .id(Long.toString(event.eventId()))
                            .name("job-progress")
                            .data(event));
                    cursor.set(event.eventId());
                }
                if (service.isTerminal(jobId)) {
                    for (AiJobProgressEventResponse event : service.eventsAfter(jobId, cursor.get())) {
                        emitter.send(SseEmitter.event()
                                .id(Long.toString(event.eventId()))
                                .name("job-progress")
                                .data(event));
                        cursor.set(event.eventId());
                    }
                    emitter.complete();
                    close.run();
                    return;
                }
                long now = System.currentTimeMillis();
                if (now - lastHeartbeat.get() >= HEARTBEAT_INTERVAL_MILLIS) {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                    lastHeartbeat.set(now);
                }
            } catch (IOException | RuntimeException error) {
                emitter.completeWithError(error);
                close.run();
            }
        };

        emitter.onCompletion(close);
        emitter.onTimeout(close);
        emitter.onError(error -> close.run());
        future.set(scheduler.scheduleWithFixedDelay(
                poll,
                Instant.now().plusMillis(10),
                Duration.ofMillis(pollIntervalMillis)));
        return emitter;
    }
}
