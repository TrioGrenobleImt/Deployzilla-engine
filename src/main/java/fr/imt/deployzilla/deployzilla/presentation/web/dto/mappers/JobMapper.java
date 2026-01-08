package fr.imt.deployzilla.deployzilla.presentation.web.dto.mappers;

import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Job;
import fr.imt.deployzilla.deployzilla.presentation.web.dto.JobResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface JobMapper {
    JobResponse toResponse(Job job);
}
