package fr.imt.deployzilla.deployzilla.presentation.web;

import lombok.Value;

@Value
public class PipelineCreationRequest {

    String projectId;

    String commitHash;

    String author;

}
