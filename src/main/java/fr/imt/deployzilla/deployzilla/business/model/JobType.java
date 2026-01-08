package fr.imt.deployzilla.deployzilla.business.model;

import lombok.Getter;

@Getter
public enum JobType {
    CLONE("CLONE"),
    NPM_INSTALL("NPM-INSTALL"),
    NPM_LINT("NPM-LINT"),
    NPM_TEST("NPM-TEST"),
    SONAR("SONAR"),
    NPM_BUILD("NPM-BUILD"),
    IMAGE_BUILD("IMAGE-BUILD"),
    APP_RUN("APP-RUN");

    private final String commandName;

    JobType(String commandName) {
        this.commandName = commandName;
    }
}
