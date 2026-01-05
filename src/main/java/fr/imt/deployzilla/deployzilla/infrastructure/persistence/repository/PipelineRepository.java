package fr.imt.deployzilla.deployzilla.infrastructure.persistence.repository;

import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Pipeline;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PipelineRepository extends MongoRepository<Pipeline, String> {
}
