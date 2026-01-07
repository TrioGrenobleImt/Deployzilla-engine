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

CMD ["/run-node-script.sh", "test"]

# Stage 4: NPM Install
FROM node:22-alpine AS npm-install

WORKDIR /workspace
RUN corepack enable

COPY scripts/run-node-script.sh /run-node-script.sh
RUN chmod +x /run-node-script.sh

CMD ["/run-node-script.sh", "install"]

