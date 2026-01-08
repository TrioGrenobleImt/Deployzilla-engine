package fr.imt.deployzilla.deployzilla.infrastructure.service;

import lombok.Value;

@Value
public class SonarTokenResponse {

    String login;

    String name;

    String token;

    String createdAt;

}
