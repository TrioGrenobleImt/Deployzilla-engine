package fr.imt.deployzilla.deployzilla.infrastructure.websocket;

import fr.imt.deployzilla.deployzilla.business.model.PipelineStatusUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RedisPipelineStatusSubscriber {

    private final SimpMessagingTemplate messagingTemplate;

    public RedisPipelineStatusSubscriber(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void onMessage(String messageBody) {
        try {
            String[] parts = messageBody.split("\\|");
            if (parts.length < 2) return;

            String pipelineId = parts[0];
            String status = parts[1];
            String currentStep = parts.length > 2 ? parts[2] : null;

            PipelineStatusUpdate update = PipelineStatusUpdate.builder()
                    .pipelineId(pipelineId)
                    .status(status)
                    .currentStep(currentStep)
                    .build();

            log.info("Broadcasting pipeline status update for {}", pipelineId);
            messagingTemplate.convertAndSend("/topic/pipeline-status/" + pipelineId, update);
        } catch (Exception e) {
            log.error("Failed to parse pipeline status update", e);
        }
    }
}
