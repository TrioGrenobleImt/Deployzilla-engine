package fr.imt.deployzilla.deployzilla.business.command;

import fr.imt.deployzilla.deployzilla.business.service.JobService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

@Service
public class CommandFactory {

    private final Map<String, BiFunction<String, String, Command>> registry = new HashMap<>();

    public CommandFactory(JobService jobService) {

        registry.put("CLONE", (projId, pipeId) ->
                new FetchProjectCommand(jobService, projId, pipeId));
    }

    public Command create(String commandName, String projectId, String pipelineId) {
        return registry.getOrDefault(commandName, (p, pipe) -> {
            throw new IllegalArgumentException("Unknown command: " + commandName);
        }).apply(projectId, pipelineId);
    }

}
