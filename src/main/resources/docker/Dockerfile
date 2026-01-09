# Stage 1: Git Clone
FROM alpine/git:v2.52.0 AS git-clone

COPY scripts/clone.sh /clone.sh
RUN chmod +x /clone.sh

ENTRYPOINT ["/clone.sh"]

# Stage 2: NPM Lint
FROM node:22-alpine AS npm-lint

WORKDIR /workspace
RUN corepack enable

COPY scripts/run-node-script.sh /run-node-script.sh
RUN chmod +x /run-node-script.sh

CMD ["/run-node-script.sh", "lint"]

# Stage 3: NPM Test
FROM node:22-alpine AS npm-test

WORKDIR /workspace
RUN corepack enable

COPY scripts/run-node-script.sh /run-node-script.sh
RUN chmod +x /run-node-script.sh

CMD ["/run-node-script.sh", "test", "--coverage"]

# Stage 4: NPM Install
FROM node:22-alpine AS npm-install

WORKDIR /workspace
RUN corepack enable

COPY scripts/run-node-script.sh /run-node-script.sh
RUN chmod +x /run-node-script.sh

CMD ["/run-node-script.sh", "install"]

# Stage 5 : SonarQube analysis
FROM sonarsource/sonar-scanner-cli:latest AS sonarqube

USER root

WORKDIR /workspace

# 5. Runtime Command
# We override the ENTRYPOINT to run a shell.
# This ensures that ${SONAR_PROJECT_KEY} is replaced by its value at runtime.
ENTRYPOINT ["/bin/sh", "-c"]

# Run the scanner passing the env vars
CMD ["sonar-scanner -Dsonar.projectKey=${SONAR_PROJECT_KEY} -Dsonar.sources=. ${SONAR_ARGS}"]

# Stage 6: NPM Build
FROM node:22-alpine AS npm-build

WORKDIR /workspace
RUN corepack enable

COPY scripts/run-node-script.sh /run-node-script.sh
RUN chmod +x /run-node-script.sh

CMD ["/run-node-script.sh", "build"]
