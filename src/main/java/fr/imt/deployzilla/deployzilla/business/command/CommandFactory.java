package fr.imt.deployzilla.deployzilla.business.command;

import fr.imt.deployzilla.deployzilla.business.service.JobService;
import fr.imt.deployzilla.deployzilla.business.model.JobType;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

@Service
public class CommandFactory {

    private final Map<String, BiFunction<String, String, Command>> registry = new HashMap<>();

    public CommandFactory(JobService jobService) {

        registry.put(JobType.CLONE.getCommandName(), (projectId, pipelineId) ->
                new GitCloneCommand(jobService, projectId, pipelineId));

        registry.put(JobType.NPM_INSTALL.getCommandName(), (projectId, pipelineId) ->
                new RunNpmInstallCommand(jobService, pipelineId));

        registry.put(JobType.NPM_LINT.getCommandName(), (projectId, pipelineId) ->
                new RunEslintCommand(jobService, pipelineId));

        registry.put(JobType.NPM_TEST.getCommandName(), (projectId, pipelineId) ->
                new RunUnitTestsCommand(jobService, pipelineId));

        registry.put(JobType.SONAR.getCommandName(), (projectId, pipelineId) ->
                new RunSonarAnalysisCommand(jobService, pipelineId));
    }

    public Command create(String commandName, String projectId, String pipelineId) {
        return registry.getOrDefault(commandName, (p, pipe) -> {
            throw new IllegalArgumentException("Unknown command: " + commandName);
        }).apply(projectId, pipelineId);
    }

}
