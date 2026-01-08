package fr.imt.deployzilla.deployzilla.business.port;

import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Project;

import java.util.Optional;

public interface ProjectRepositoryPort {
    Optional<Project> findById(String projectId);
}
