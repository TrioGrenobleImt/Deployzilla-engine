package fr.imt.deployzilla.deployzilla.business.port;

public interface ProcessLogPublisherPort {
    void publish(String pipelineId, String message);
}
