package fr.imt.deployzilla.deployzilla.business.model;

import lombok.Value;

@Value
public class ProcessResult {

    int exitCode;

    String output;

}
