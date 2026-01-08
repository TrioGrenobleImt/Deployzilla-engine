package fr.imt.deployzilla.deployzilla.business.port;

public interface PipelineStatusPublisherPort {
    void publish(String pipelineId, String status, String currentStep);
}
