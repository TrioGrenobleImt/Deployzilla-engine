package fr.imt.deployzilla.deployzilla.business.command;

import fr.imt.deployzilla.deployzilla.business.model.ProcessResult;

@FunctionalInterface
public interface Command {

    ProcessResult execute();

}
