# Deployzilla Engine

**Deployzilla Engine** is a robust, Java-based CI/CD pipeline orchestrator designed to automate the lifecycle of application deployment. Built with Spring Boot, it manages the entire flow from code commit to production deployment on remote servers, leveraging Docker containers for process isolation and consistency.

## 🚀 Project Overview

The engine serves as the core backend for the Deployzilla platform. It is responsible for receiving build triggers, spinning up isolated environments for testing and building, and securely deploying applications to remote infrastructure via SSH tunneling.

Unlike traditional CI tools that rely on shell scripts on the host, Deployzilla creates ephemeral Docker containers for every step of the pipeline (Git Clone, NPM Install, Unit Tests, etc.), ensuring a clean state for every build.

## ✨ Main Features

* **Remote Deployment Strategy**: Connects to remote servers via SSH Tunneling to execute Docker commands securely, removing the need to expose the remote Docker daemon publicly.
* **Containerized Pipeline Steps**: Every step (Clone, Lint, Test, Build) runs in a specific, isolated Docker container defined in `resources/docker/Dockerfile`.
* **Real-Time Monitoring**: Uses **Redis Pub/Sub** and **WebSockets** to stream build logs and pipeline status changes to the frontend in real-time.
* **Automated Reverse Proxy**: Automatically injects **Traefik** labels during deployment to handle domain routing (`app.domain.com`) and SSL certificates via Let's Encrypt.
* **Quality Integration**: Built-in support for **SonarQube** analysis to enforce code quality gates before deployment.
* **Secure Secrets Management**: Handles SSH keys for private repositories and generates on-the-fly Docker contexts.

## 🏗️ Software Architecture

The project strictly follows **Hexagonal Architecture (Ports and Adapters)** to decouple the core business logic from external frameworks and tools.

### 1. Hexagonal Structure
* **Domain (Business)**: Contains the core logic (`PipelineService`, `JobService`), models (`Pipeline`, `Job`), and interfaces (`Ports`). This layer has no dependency on the database or the web framework.
* **Input Adapters (Presentation)**: The REST API (`PipelineController`) and WebSocket controllers that drive the application.
* **Output Adapters (Infrastructure)**: Implementations of the ports, such as `MongoPipelineRepositoryAdapter` (Persistence), `RedisProcessLogPublisherAdapter` (Messaging), and `ContainerExecutor` (Docker Infrastructure).

### 2. Design Patterns
* **Command Pattern**: The pipeline execution logic is encapsulated into `Command` objects (e.g., `GitCloneCommand`, `RunUnitTestsCommand`). This allows for a decoupled execution flow and easy rollback mechanisms.
* **Factory Pattern**: A `CommandFactory` is used to instantiate the correct `Command` implementation based on the `JobType` enum, making the system easily extensible for new job types.
* **Strategy Pattern**: The `ContainerExecutor` acts as a strategy for executing logic, switching between local and remote Docker clients based on configuration.

## 📂 Project Structure

```text
src/main/java/fr/imt/deployzilla/deployzilla
├── business
│   ├── command           # Command Pattern implementations (GitClone, NpmInstall, etc.)
│   ├── model             # Domain models (POJOs)
│   ├── port              # Output ports (Interfaces for Repo, Publishers)
│   ├── service           # Core business logic (PipelineService, JobService)
│   └── utils             # Utilities (DirectorySanitizer)
├── infrastructure
│   ├── client            # External Feign Clients (SonarQube)
│   ├── persistence       # MongoDB Entities and Repository Adapters
│   ├── redis             # Redis Adapters for Pub/Sub
│   └── websocket         # WebSocket subscribers
└── presentation
    └── web               # REST Controllers, DTOs, and Exception Handlers
src/main/resources
├── docker                # Dockerfiles and scripts for pipeline steps
└── application.yml       # Configuration
```

## 🛠️ Technology Stack
+ Language: Java 21
+ Framework: Spring Boot 3
+ Database: MongoDB (Metadata storage)
+ Messaging: Redis (Log streaming & Status updates)
+ Containerization: Docker & Docker-Java
+ Analysis: SonarQube

## 👥 Authors
@CHAMPEIX_Cédric
@DELASSUS_Félix
@ENDIGNOUS_Arnaud
@VILLET_Téo
