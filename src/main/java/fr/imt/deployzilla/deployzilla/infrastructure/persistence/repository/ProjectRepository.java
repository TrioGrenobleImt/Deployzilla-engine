package fr.imt.deployzilla.deployzilla.infrastructure.persistence.repository;

import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Project;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends MongoRepository<Project, String> {
}
