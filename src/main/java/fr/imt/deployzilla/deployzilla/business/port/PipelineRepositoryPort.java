package fr.imt.deployzilla.deployzilla.business.port;

import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Pipeline;

import java.util.Optional;

public interface PipelineRepositoryPort {
    Pipeline save(Pipeline pipeline);
    Optional<Pipeline> findById(String pipelineId);
    void updateCommitHash(String pipelineId, String commitHash);
}
