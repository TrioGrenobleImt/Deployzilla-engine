package fr.imt.deployzilla.deployzilla.infrastructure.persistence.client;

import fr.imt.deployzilla.deployzilla.infrastructure.service.SonarTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "sonarClient", url = "${sonar.url:http://localhost:9000}")
public interface SonarQubeClient {

    @PostMapping("/api/user_tokens/generate?name=ci_token&type=GLOBAL_ANALYSIS_TOKEN")
    public SonarTokenResponse fetchSonarTokengenerateToken(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("name") String tokenName,
            @RequestParam("type") String tokenType
    );

}
