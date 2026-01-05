package fr.imt.deployzilla.deployzilla.business.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisLogSubscriber {

    private final SimpMessagingTemplate messagingTemplate;

    public RedisLogSubscriber(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * This method is triggered by the MessageListenerAdapter
     * whenever Redis receives a message on the topic.
     */
    public void onMessage(String messageBody) {
        // 1. Parse our simple protocol "pipelineId|logMessage"
        int delimiterIndex = messageBody.indexOf('|');
        if (delimiterIndex == -1) return;

        String pipelineId = messageBody.substring(0, delimiterIndex);
        String logContent = messageBody.substring(delimiterIndex + 1);

        messagingTemplate.convertAndSend("/topic/logs/" + pipelineId, logContent);
    }
}