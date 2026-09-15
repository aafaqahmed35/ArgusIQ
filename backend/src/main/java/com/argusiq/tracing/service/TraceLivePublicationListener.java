package com.argusiq.tracing.service;

import com.argusiq.tracing.event.TraceLivePublicationRequested;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TraceLivePublicationListener {

    static final String TRACE_TOPIC = "/topic/traces";

    private final SimpMessagingTemplate messagingTemplate;

    public TraceLivePublicationListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAfterCommit(TraceLivePublicationRequested event) {
        messagingTemplate.convertAndSend(TRACE_TOPIC, event.trace());
    }
}
