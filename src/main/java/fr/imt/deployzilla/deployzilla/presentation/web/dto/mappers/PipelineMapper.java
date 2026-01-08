package fr.imt.deployzilla.deployzilla.presentation.web.dto.mappers;

import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Pipeline;
import fr.imt.deployzilla.deployzilla.presentation.web.dto.PipelineResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = JobMapper.class)
public interface PipelineMapper {
    PipelineResponse toResponse(Pipeline pipeline);
}
