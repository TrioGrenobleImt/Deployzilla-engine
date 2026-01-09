# Deployzilla Engine

## 1. Project Overview

**Deployzilla Engine** is a robust Java Spring Boot orchestrator designed to automate the lifecycle of web applications. It acts as a CI/CD platform that takes a GitHub repository, runs it through a defined pipeline (installation, linting, testing, quality analysis), builds a Docker image, and deploys it to a remote VPS.

The system solves the problem of manual deployments by providing a fully automated, containerized pipeline that ensures code quality via SonarQube and seamless deployment with automatic SSL handling via Traefik. A typical use case involves a developer triggering a pipeline via webhook or API commit, which results in a live, accessible web application on a subdomain (e.g., `myproject.deployzilla.endignous.fr`).

## 2. Architecture & Design

The application follows a **Hexagonal Architecture** (Ports & Adapters) to separate business logic from infrastructure concerns.

### High-Level Architecture
1.  **Orchestrator (Spring Boot)**: The core brain that manages pipeline state, dispatches jobs, and coordinates with external systems.
2.  **Persistence Layer**:
    -   **MongoDB**: Stores permanent state (Projects, Pipelines, Jobs, Environment Variables).
    -   **Redis**: Handles ephemeral data (Real-time logs, Pipeline Status Pub/Sub).
3.  **Execution Engine (Docker)**:
    -   **Local Docker**: Used for building images and pushing to a registry.
    -   **Remote Docker (VPS)**: Used for running the final application containers. The orchestrator connects via an **SSH Tunnel** to the remote Docker socket.
4.  **Observability**:
    -   **WebSockets**: Real-time log streaming to the frontend.
    -   **SonarQube**: Integrated code quality analysis.

### Key Components
-   **`PipelineService`**: Manages the 8-step execution flow.
-   **`ContainerExecutor`**: A sophisticated wrapper around the Docker Client. It handles:
    -   Local vs. Remote Docker context switching.
    -   SSH Tunnel lifecycle for remote execution.
    -   Container isolation using volumes and labels.
-   **`CommandFactory`**: Implements the Command Pattern to instantiate steps like `GitCloneCommand`, `RunNpmTestCommand`, etc.
-   **`ImageBuildService`**: Dynamically generates a multi-stage `Dockerfile` based on the project type (detects `yarn`, `pnpm`, or `npm`) to ensure a clean, reproducible build.

## 3. Features

-   **Automated Pipeline**: 8 predefined steps: `CLONE`, `NPM_INSTALL`, `NPM_LINT`, `NPM_TEST`, `SONAR`, `NPM_BUILD`, `IMAGE_BUILD`, `APP_RUN`.
-   **Smart Package Manager Detection**: Automatically detects and uses `pnpm`, `yarn`, or `npm`.
-   **Secure Git Cloning**: Supports public HTTPS and private SSH repositories (using isolated deploy keys).
-   **Remote Deployment**: Deploys applications to a remote VPS with automatic **Traefik** configuration (Reverse Proxy, SSL/TLS, Domain Routing).
-   **Quality Gate**: Integrated SonarQube analysis step.
-   **Live Feedback**: Real-time logging of every step via Redis & WebSockets.
-   **Docker Registry Integration**: Builds images locally and pushes them to a private registry for the remote host to pull.

## 4. Requirements

### System Requirements
-   **Java**: JDK 21
-   **Maven**: 3.8+
-   **Docker**: Desktop or Engine 24+
-   **Docker Compose**: V2

### Remote VPS Prerequisites
If `deployzilla.remote.enabled` is `true`, the target server must have:
-   **Docker Engine** installed and running.
-   **SSH Server** enabled.
-   **User Account**: A user (defined in config) with permissions to access `/var/run/docker.sock`.
-   **Traefik**: Running and configured to listen for Docker labels (as the engine adds Traefik labels to deployed apps).

## 5. Configuration

Configuration is managed via `src/main/resources/application.yml` and environment variables.

### Key Environment Variables

| Variable | Description | Default |
| :--- | :--- | :--- |
| `SPRING_DATA_MONGODB_URI` | MongoDB Connection URI | `mongodb://localhost:27017/deployzilla` |
| `SPRING_DATA_REDIS_HOST` | Redis Host | `localhost` |
| `REDIS_PASSWORD` | Redis Password | `admin` |
| `MONGODB_URI` | MongoDB URI override | `mongodb://mongo:27017/deployzilla` |
| `SONAR_USERNAME` | SonarQube admin username | `admin` |
| `SONAR_PASSWORD` | SonarQube admin password | *(Required)* |
| `SONAR_URL` | SonarQube server URL | `http://sonarqube:9000` |
| `SONAR_DB_USER` | SonarQube DB User | `sonar` |
| `SONAR_DB_PASSWORD` | SonarQube DB Password | `sonar` |
| `DEPLOYZILLA_REMOTE_USER` | SSH User for remote VPS | *(Required)* |
| `DEPLOYZILLA_REMOTE_PASSWORD`| SSH Password for remote VPS | *(Required)* |
| `DEPLOYZILLA_REMOTE_PORT` | SSH Port for remote VPS | `22` |
| `DOCKER_REGISTRY_USER` | Docker Registry Username for image push/pull | *(Required)* |
| `DOCKER_REGISTRY_PASSWORD` | Docker Registry Password | *(Required)* |

### Application.yml Highlights
-   **Workspace**: `deployzilla.workspace.path` defines where temporary files are stored.
-   **Remote Execution**:
    ```yaml
    deployzilla:
      remote:
        enabled: true
        host: "147.79.114.156" # Target Deployment Server
    ```
-   **Docker Registry**: Configured under `deployzilla.docker.registry`.

## 6. Installation & Setup

1.  **Clone the Repository**:
    ```bash
    git clone https://github.com/your-org/deployzilla-engine.git
    cd deployzilla-engine
    ```

2.  **Build the Application**:
    ```bash
    ./mvnw clean install
    ```

3.  **Start Infrastructure**:
    The project includes a `docker-compose.yml` that starts Redis, Mongo, SonarQube, and builder helper images.
    ```bash
    docker-compose up -d --build
    ```
    *Note: The `app` service in docker-compose runs the engine itself, but you can also run it via IDE.*

4.  **Helper Images**:
    The `docker-compose.yml` builds several helper images (`deployzilla/step:git-clone`, `npm-install`, etc.) required for the pipeline jobs. Ensure these are built successfully.

## 7. Running the Application

### Local Development
Run the Spring Boot application using Maven or your IDE:
```bash
./mvnw spring-boot:run
```
Ensure you have the environment variables set (especially secrets) in your run configuration or `.env` file if using a loader.

### Docker Production Mode
The `docker-compose.yml` defines the `app` service which listens on port `8081`.
```bash
docker-compose up -d app
```

## 8. API Documentation

### Start a Pipeline
Triggers the full deployment lifecycle for a project.

-   **URL**: `/api/v1/pipelines/start`
-   **Method**: `POST`
-   **Content-Type**: `application/json`

**Request Body**:
```json
{
  "projectId": "654321abcdef...",
  "commitHash": "e5c6e8...", // Optional, verified during CLONE
  "author": "John Doe",
  "trigger": "WEBHOOK" // or "MANUAL"
}
```

**Response**:
```json
{
  "id": "pipeline-12345",
  "status": "CREATED",
  "jobs": [...]
}
```

## 9. Deployment Workflow

1.  **Initialization**: API creates a pipeline entry in MongoDB with status `CREATED`.
2.  **Clone**: `GitCloneService` clones the repo to verify it exists and retreive the commit hash. It saves this to a shared Docker volume.
3.  **Analysis**:
    -   `NPM_INSTALL`: Installs dependencies in an isolated container.
    -   `NPM_LINT` & `NPM_TEST`: Runs scripts defined in `package.json`.
    -   `SONAR`: Runs SonarQube analysis against the local instance.
4.  **Artifact Creation**:
    -   `NPM_BUILD`: Creates the production build (e.g., `dist/`).
    -   **Image Build**: Generates a standard Dockerfile (Alpine Node) that clones & builds the project *inside* the Docker build context to ensure a clean artifact. Pushes the result to the configured Registry.
5.  **Deployment**:
    -   `APP_RUN`: Connects to the **Remote VPS** (via SSH Tunnel).
    -   Pulls the image from the registry.
    -   Starts the container with Traefik labels (`Host=projectname.deployzilla...`).

## 10. Logging, Monitoring & Debugging

-   **Logs**:
    -   **Application Logs**: Standard Spring Boot logs (console/file).
    -   **Process Logs**: Every step (git clone, npm install) streams stdout/stderr to **Redis**.
    -   **Access**: Frontend consumes these via WebSocket subscription.
-   **Debugging**:
    -   If a pipeline fails, check the `Job` status in MongoDB.
    -   Check the `ProcessLog` in Redis/Mongo for the specific error output from the container.

## 11. Security Considerations

-   **SSH Tunneling**: The engine uses `sshpass` to create an encrypted tunnel for Docker control. This avoids exposing the Docker socket publicly.
-   **Secret Isolation**: SSH Private keys for cloning private repos are written to a temporary, permission-locked file (`600`) and deleted immediately after the clone step.
-   **Container Isolation**: Each step runs in a fresh container.
-   **Credentials**: Sensitive data (Registry/SSH passwords) are injected via Environment Variables and are not stored in the git repo.
-   **Warning**: `EnvVar` entities in `Project` are currently stored as plain text in the database. Ensure the MongoDB instance is secured.

## 12. Contributing

The project is structured by feature (Hexagonal):
-   `business/command`: Implement `Command` interface to add new pipeline steps.
-   `business/service/jobs`: Add the logic for the new step here.
-   `infrastructure`: Add new adapters for different persistence or messaging backends.

To add a new step:
1.  Create a `JobType` enum entry.
2.  Implement a `Command` class.
3.  Register it in `CommandFactory`.
4.  Add the logic in a specialized Service.

## 👥 Authors
@CHAMPEIX_Cédric
@DELASSUS_Félix
@ENDIGNOUS_Arnaud
@VILLET_Téo