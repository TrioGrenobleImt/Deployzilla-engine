package fr.imt.deployzilla.deployzilla.business.model;

import lombok.Value;

@Value
public class SonarTokenResponse {

    String login;

    String name;

    String token;

    String createdAt;

}
