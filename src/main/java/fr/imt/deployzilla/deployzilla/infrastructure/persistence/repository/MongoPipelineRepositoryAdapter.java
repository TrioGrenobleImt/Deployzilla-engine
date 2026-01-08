package fr.imt.deployzilla.deployzilla.infrastructure.persistence.repository;

import fr.imt.deployzilla.deployzilla.business.port.PipelineRepositoryPort;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Pipeline;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MongoPipelineRepositoryAdapter implements PipelineRepositoryPort {

    private final PipelineRepository pipelineRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public Pipeline save(Pipeline pipeline) {
        return pipelineRepository.save(pipeline);
    }

    @Override
    public Optional<Pipeline> findById(String pipelineId) {
        return pipelineRepository.findById(pipelineId);
    }

    @Override
    public void updateCommitHash(String pipelineId, String commitHash) {
        Query query = Query.query(Criteria.where("_id").is(pipelineId));
        Update update = Update.update("commitHash", commitHash);
        mongoTemplate.updateFirst(query, update, Pipeline.class);
    }
}
