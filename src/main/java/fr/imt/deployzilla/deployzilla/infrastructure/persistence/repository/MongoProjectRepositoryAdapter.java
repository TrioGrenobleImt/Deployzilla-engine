package fr.imt.deployzilla.deployzilla.infrastructure.persistence.repository;

import fr.imt.deployzilla.deployzilla.business.port.ProjectRepositoryPort;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Project;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MongoProjectRepositoryAdapter implements ProjectRepositoryPort {

    private final ProjectRepository projectRepository;

    @Override
    public Optional<Project> findById(String projectId) {
        if (!ObjectId.isValid(projectId)) {
            return Optional.empty();
        }
        return projectRepository.findById(new ObjectId(projectId));
    }
}
