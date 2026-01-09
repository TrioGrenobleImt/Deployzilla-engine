# Deployzilla Engine - Migration & Refactoring Guide

> **Architectural Improvement Roadmap**  
> Based on comprehensive code review - January 2026

---

## Executive Summary

This guide outlines a phased approach to improving the Deployzilla architecture, from quick wins to long-term strategic changes. Each phase builds on the previous, ensuring stability while progressively enhancing maintainability, testability, and scalability.

---

## Phase 1: Short-Term (1-2 Sprints) - Safe Refactors

**Goal**: Reduce technical debt with low-risk, high-value changes.

---

### TICKET P1-01: Extract SSH Tunnel to `SshTunnelManager` ✅

**Priority**: High  
**Effort**: 2 hours  
**Dependencies**: None  
**Assignee**: _TBD_

#### Description

Extract SSH tunnel management logic from `ContainerExecutor` into a dedicated `SshTunnelManager` class to improve separation of concerns and enable independent testing.

#### Current State

- SSH tunnel logic is embedded in `ContainerExecutor.java` (lines 130-180)
- `startSshTunnel()` method handles process creation, environment variables, and output streaming
- Tunnel process is stored as instance variable `sshTunnelProcess`
- No abstraction layer for future SSH library replacement

#### Files to Create

| File | Purpose |
|------|---------|
| `src/main/java/fr/imt/deployzilla/deployzilla/infrastructure/ssh/SshTunnel.java` | Interface defining tunnel operations |
| `src/main/java/fr/imt/deployzilla/deployzilla/infrastructure/ssh/SshpassTunnelManager.java` | Current implementation using sshpass |

#### Files to Modify

| File | Changes |
|------|---------|
| `src/main/java/fr/imt/deployzilla/deployzilla/business/service/ContainerExecutor.java` | Remove SSH methods, inject `SshTunnel` dependency |

#### Implementation Steps

1. Create interface `SshTunnel`:
   ```java
   public interface SshTunnel {
       void connect() throws SshException;
       void disconnect();
       boolean isConnected();
       int getLocalPort();
   }
   ```

2. Create `SshpassTunnelManager` implementing `SshTunnel`:
   - Move `startSshTunnel()` logic from `ContainerExecutor`
   - Move `sshTunnelProcess` field
   - Move SSH-related `@Value` configurations
   - Add `@Service` annotation

3. Modify `ContainerExecutor`:
   - Inject `SshTunnel` via constructor
   - Replace direct `startSshTunnel()` call with `sshTunnel.connect()`
   - Remove SSH-related fields and methods
   - Update `cleanup()` to call `sshTunnel.disconnect()`

4. Add configuration binding:
   ```java
   @ConfigurationProperties(prefix = "deployzilla.remote")
   public record SshConfiguration(
       boolean enabled,
       String host,
       String user,
       String password,
       int port
   ) {}
   ```

#### Acceptance Criteria

- [ ] `SshTunnel` interface created with `connect()`, `disconnect()`, `isConnected()`, `getLocalPort()`
- [ ] `SshpassTunnelManager` class extracts all SSH logic from `ContainerExecutor`
- [ ] `ContainerExecutor` uses injected `SshTunnel` instead of inline SSH code
- [ ] `ContainerExecutor` reduced by ~60 lines
- [ ] Application starts successfully with remote enabled
- [ ] SSH tunnel establishes connection to remote VPS
- [ ] Logs show "[SSH-TUNNEL]" messages from new class

#### Testing

**Manual Testing**:
1. Start application with `deployzilla.remote.enabled=true`
2. Verify SSH tunnel connects (check logs for "SSH Tunnel started successfully")
3. Trigger a pipeline and verify remote container deployment works
4. Stop application and verify tunnel cleanup in logs

**Unit Testing** (to add in future):
- Mock `SshTunnel` in `ContainerExecutor` tests
- Test `SshpassTunnelManager` with process mock

---

### TICKET P1-02: Extract Image Operations to `DockerImageService` ✅

**Priority**: High  
**Effort**: 2 hours  
**Dependencies**: None  
**Assignee**: _TBD_

#### Description

Extract Docker image build and push operations from `ContainerExecutor` into a dedicated `DockerImageService` class.

#### Current State

- `buildImageLocal()` method in `ContainerExecutor` (lines 442-469)
- `pushImage()` method in `ContainerExecutor` (lines 471-496)
- Deprecated `buildImage()` wrapper method
- Registry credentials accessed directly via `@Value`

#### Files to Create

| File | Purpose |
|------|---------|
| `src/main/java/fr/imt/deployzilla/deployzilla/infrastructure/docker/DockerImageService.java` | Image build and push operations |

#### Files to Modify

| File | Changes |
|------|---------|
| `src/main/java/fr/imt/deployzilla/deployzilla/business/service/ContainerExecutor.java` | Remove image methods, keep container operations |
| `src/main/java/fr/imt/deployzilla/deployzilla/business/service/jobs/ImageBuildService.java` | Inject `DockerImageService` instead of `ContainerExecutor` |

#### Implementation Steps

1. Create `DockerImageService`:
   ```java
   @Service
   @RequiredArgsConstructor
   @Slf4j
   public class DockerImageService {
       private final ProcessLogPublisherPort logPublisher;
       
       @Value("${deployzilla.docker.registry.username:}")
       private String registryUsername;
       
       @Value("${deployzilla.docker.registry.password:}")
       private String registryPassword;
       
       private DockerClient localDockerClient;
       
       @PostConstruct
       public void init() {
           // Initialize local Docker client (copy from ContainerExecutor)
       }
       
       public String buildImage(String pipelineId, String contextPath, 
                                String dockerfileName, String imageName, String tag) {
           // Move buildImageLocal logic here
       }
       
       public void pushImage(String pipelineId, String imageName, String tag) {
           // Move pushImage logic here
       }
   }
   ```

2. Update `ImageBuildService`:
   - Replace `ContainerExecutor` injection with `DockerImageService`
   - Update method calls: `containerExecutor.buildImageLocal()` → `dockerImageService.buildImage()`
   - Update method calls: `containerExecutor.pushImage()` → `dockerImageService.pushImage()`
   - Keep `containerExecutor.publishLog()` or inject `ProcessLogPublisherPort` directly

3. Remove from `ContainerExecutor`:
   - `buildImageLocal()` method
   - `buildImage()` deprecated method
   - `pushImage()` method
   - `localDockerClient` field (move to DockerImageService)

#### Acceptance Criteria

- [ ] `DockerImageService` created with `buildImage()` and `pushImage()` methods
- [ ] `ImageBuildService` uses `DockerImageService` instead of `ContainerExecutor`
- [ ] `ContainerExecutor` no longer has image-related methods
- [ ] `ContainerExecutor` reduced by ~80 lines
- [ ] Image build pipeline step works correctly
- [ ] Image push to Docker Hub works with credentials

#### Testing

**Manual Testing**:
1. Start application
2. Create a project and trigger a pipeline
3. Verify IMAGE_BUILD step succeeds (check logs for "Starting LOCAL image build")
4. Verify image is pushed to registry (check Docker Hub or registry)
5. Verify APP_RUN step can pull the image on remote

---

### TICKET P1-03: Remove Hardcoded SonarQube Credentials ✅

**Priority**: Critical (Security)  
**Effort**: 30 minutes  
**Dependencies**: None  
**Assignee**: _TBD_

#### Description

Remove hardcoded SonarQube password from `application.yml` and use environment variables instead.

#### Current State

- `application.yml` lines 16-20 contain:
  ```yaml
  sonar:
    web:
      username: admin
      password: "SyQ7WTN2fh5ju45k8Fz!"
  ```
- Password is exposed in version control

#### Files to Modify

| File | Changes |
|------|---------|
| `src/main/resources/application.yml` | Replace hardcoded password with env var reference |
| `docker-compose.yml` | Add environment variable for SonarQube password |
| `README.md` | Document required environment variable |

#### Implementation Steps

1. Update `application.yml`:
   ```yaml
   sonar:
     web:
       username: ${SONAR_USERNAME:admin}
       password: ${SONAR_PASSWORD}
     url: ${SONAR_URL:http://sonarqube:9000}
   ```

2. Update `docker-compose.yml` (deployzilla service):
   ```yaml
   deployzilla:
     environment:
       - SONAR_USERNAME=${SONAR_USERNAME:-admin}
       - SONAR_PASSWORD=${SONAR_PASSWORD}
       # ... other env vars
   ```

3. Create `.env.example` file (if not exists):
   ```env
   # SonarQube Configuration
   SONAR_USERNAME=admin
   SONAR_PASSWORD=your_secure_password_here
   
   # Other required variables...
   ```

4. Update `README.md` with required variables section

5. Add to `.gitignore`:
   ```
   .env
   .env.local
   ```

#### Acceptance Criteria

- [ ] No hardcoded passwords in `application.yml`
- [ ] All credentials use `${ENV_VAR}` syntax
- [ ] `.env.example` file documents all required variables
- [ ] `.env` file is gitignored
- [ ] Application fails to start if `SONAR_PASSWORD` is not set (add validation)
- [ ] SonarQube analysis still works when env vars are provided

#### Testing

**Manual Testing**:
1. Remove or rename `.env` file
2. Start application → Should fail with clear error about missing `SONAR_PASSWORD`
3. Create `.env` with correct password
4. Start application → Should start successfully
5. Trigger pipeline → Sonar analysis step should complete

---

### TICKET P1-04: Introduce Domain Exception Hierarchy ✅

**Priority**: Medium  
**Effort**: 1 hour  
**Dependencies**: None  
**Assignee**: _TBD_

#### Description

Replace generic `RuntimeException` throws with a structured domain exception hierarchy for better error handling, debugging, and client responses.

#### Current State

- `ProjectNotFoundException` exists but is the only domain exception
- Most errors thrown as `RuntimeException("message")`
- Error context lost in catch blocks: `return new ProcessResult(1, "ERROR")`
- `GlobalExceptionHandler` only handles generic exceptions

#### Files to Create

| File | Purpose |
|------|---------|
| `src/.../exception/DeployzillaException.java` | Base exception class |
| `src/.../exception/PipelineExecutionException.java` | Pipeline-related errors |
| `src/.../exception/DockerOperationException.java` | Docker client errors |
| `src/.../exception/SshConnectionException.java` | SSH tunnel errors |
| `src/.../exception/ImageBuildException.java` | Image build/push failures |
| `src/.../exception/ContainerExecutionException.java` | Container runtime errors |

#### Files to Modify

| File | Changes |
|------|---------|
| `src/.../exception/ProjectNotFoundException.java` | Extend `DeployzillaException` |
| `src/.../business/service/ContainerExecutor.java` | Use specific exceptions |
| `src/.../business/service/jobs/*.java` | Use specific exceptions |
| `src/.../presentation/web/GlobalExceptionHandler.java` | Add handlers for new exceptions |

#### Implementation Steps

1. Create base exception:
   ```java
   public class DeployzillaException extends RuntimeException {
       private final String errorCode;
       
       public DeployzillaException(String errorCode, String message) {
           super(message);
           this.errorCode = errorCode;
       }
       
       public DeployzillaException(String errorCode, String message, Throwable cause) {
           super(message, cause);
           this.errorCode = errorCode;
       }
       
       public String getErrorCode() { return errorCode; }
   }
   ```

2. Create specific exceptions:
   ```java
   public class DockerOperationException extends DeployzillaException {
       public DockerOperationException(String operation, Throwable cause) {
           super("DOCKER_ERR", "Docker operation failed: " + operation, cause);
       }
   }
   
   public class SshConnectionException extends DeployzillaException {
       public SshConnectionException(String host, Throwable cause) {
           super("SSH_ERR", "Failed to connect to " + host, cause);
       }
   }
   
   public class ImageBuildException extends DeployzillaException {
       public ImageBuildException(String imageName, Throwable cause) {
           super("BUILD_ERR", "Failed to build image: " + imageName, cause);
       }
   }
   ```

3. Update `ContainerExecutor` throws:
   ```java
   // Before:
   throw new RuntimeException("Failed to start container", e);
   
   // After:
   throw new ContainerExecutionException(imageName, "start", e);
   ```

4. Update `GlobalExceptionHandler`:
   ```java
   @ExceptionHandler(DockerOperationException.class)
   public ResponseEntity<HttpResponse<Void>> handleDockerError(DockerOperationException ex) {
       log.error("Docker operation failed: {}", ex.getMessage(), ex);
       HttpResponse<Void> response = HttpResponse.error(
           ex.getErrorCode(),
           ex.getMessage()
       );
       return new ResponseEntity<>(response, HttpStatus.SERVICE_UNAVAILABLE);
   }
   ```

#### Acceptance Criteria

- [ ] `DeployzillaException` base class created with `errorCode` field
- [ ] At least 5 specific exception classes created
- [ ] `GlobalExceptionHandler` has handlers for each exception type
- [ ] No `throw new RuntimeException()` remains in codebase (search grep)
- [ ] API returns structured error responses with error codes
- [ ] Stack traces logged at ERROR level for all domain exceptions

#### Testing

**Manual Testing**:
1. Trigger a pipeline with invalid project ID → Should return 404 with `ProjectNotFoundException`
2. Stop Docker daemon and trigger pipeline → Should return 503 with `DockerOperationException`
3. Configure invalid SSH credentials → Should fail with `SshConnectionException`
4. Verify all errors include `errorCode` in JSON response

---

### TICKET P1-05: Add Retry Mechanism with Spring Retry

**Priority**: High  
**Effort**: 2 hours  
**Dependencies**: None  
**Assignee**: _TBD_

#### Description

Add automatic retry capability for transient failures in Docker operations and external service calls using Spring Retry.

#### Current State

- No retry mechanism for any operations
- Network timeouts or transient Docker errors cause immediate pipeline failure
- Users must manually restart entire pipeline

#### Files to Create

| File | Purpose |
|------|---------|
| `src/.../configuration/RetryConfiguration.java` | Spring Retry beans and templates |

#### Files to Modify

| File | Changes |
|------|---------|
| `pom.xml` | Add spring-retry dependency |
| `src/.../DeployzillaApplication.java` | Add `@EnableRetry` annotation |
| `src/.../business/service/ContainerExecutor.java` | Add `@Retryable` to key methods |
| `src/.../business/service/jobs/GitCloneService.java` | Add `@Retryable` to clone |
| `src/.../business/service/SonarqubeService.java` | Add `@Retryable` to API calls |

#### Implementation Steps

1. Add dependency to `pom.xml`:
   ```xml
   <dependency>
       <groupId>org.springframework.retry</groupId>
       <artifactId>spring-retry</artifactId>
   </dependency>
   <dependency>
       <groupId>org.springframework</groupId>
       <artifactId>spring-aspects</artifactId>
   </dependency>
   ```

2. Enable retry in main application:
   ```java
   @SpringBootApplication
   @EnableRetry
   public class DeployzillaApplication { }
   ```

3. Create retry configuration:
   ```java
   @Configuration
   public class RetryConfiguration {
       
       @Bean
       public RetryTemplate dockerRetryTemplate() {
           return RetryTemplate.builder()
               .maxAttempts(3)
               .exponentialBackoff(1000, 2, 10000)
               .retryOn(DockerOperationException.class)
               .build();
       }
   }
   ```

4. Add `@Retryable` to critical methods:
   ```java
   @Retryable(
       retryFor = {DockerException.class, IOException.class},
       maxAttempts = 3,
       backoff = @Backoff(delay = 2000, multiplier = 2)
   )
   public void pullImageIfNeeded(DockerClient client, String pipelineId, String image) {
       // Existing logic
   }
   
   @Recover
   public void pullImageRecover(DockerException e, DockerClient client, 
                                String pipelineId, String image) {
       log.error("Failed to pull image after retries: {}", image);
       throw new ImagePullException(image, e);
   }
   ```

5. Add retry to service methods:
   - `ContainerExecutor.pullImageIfNeeded()` - retry on pull failures
   - `ContainerExecutor.startContainer()` - retry on container start failures
   - `GitCloneService.execute()` - retry on git clone failures (network issues)
   - `SonarqubeService.getSonarToken()` - retry on API timeouts

#### Acceptance Criteria

- [ ] spring-retry dependency added to `pom.xml`
- [ ] `@EnableRetry` added to main application class
- [ ] At least 4 methods annotated with `@Retryable`
- [ ] `@Recover` methods log failures and throw appropriate exceptions
- [ ] Retry attempts are logged with attempt count
- [ ] Configurable max attempts via `application.yml`

#### Testing

**Manual Testing**:
1. Configure a slow network or use Docker Compose with network latency
2. Trigger a pipeline
3. Observe logs for retry attempts (should see "Retry attempt 2 of 3" messages)
4. Verify pipeline succeeds after transient failure

**Integration Test** (future):
```java
@Test
void shouldRetryOnDockerPullFailure() {
    // Mock DockerClient to fail first 2 times
    // Verify 3 attempts made
    // Verify success on 3rd attempt
}
```

---

### TICKET P1-06: Extract Log Streaming to `ContainerLogStreamer` ✅

**Priority**: Medium  
**Effort**: 1 hour  
**Dependencies**: None  
**Assignee**: _TBD_

#### Description

Extract container log streaming logic from `ContainerExecutor` into a dedicated `ContainerLogStreamer` class for better separation of concerns.

#### Current State

- `streamLogs()` method in `ContainerExecutor` (lines 371-400)
- `monitorContainerLogs()` method in `ContainerExecutor` (lines 565-590)
- Duplicate Javadoc comments (lines 371-376)
- Log streaming mixed with container execution logic

#### Files to Create

| File | Purpose |
|------|---------|
| `src/.../infrastructure/docker/ContainerLogStreamer.java` | Log streaming operations |

#### Files to Modify

| File | Changes |
|------|---------|
| `src/.../business/service/ContainerExecutor.java` | Inject and use `ContainerLogStreamer` |

#### Implementation Steps

1. Create `ContainerLogStreamer`:
   ```java
   @Component
   @RequiredArgsConstructor
   @Slf4j
   public class ContainerLogStreamer {
       
       private final ProcessLogPublisherPort logPublisher;
       
       @Value("${docker.timeout.seconds:600}")
       private int timeoutSeconds;
       
       /**
        * Stream container logs synchronously, capturing output.
        */
       public void streamLogs(DockerClient client, String pipelineId, 
                             String containerId, StringBuilder outputBuffer) {
           try {
               client.logContainerCmd(containerId)
                   .withStdOut(true)
                   .withStdErr(true)
                   .withFollowStream(true)
                   .exec(new ResultCallback.Adapter<Frame>() {
                       @Override
                       public void onNext(Frame frame) {
                           String logLine = new String(frame.getPayload()).trim();
                           if (!logLine.isEmpty()) {
                               logPublisher.publish(pipelineId, logLine);
                               if (StreamType.STDOUT.equals(frame.getStreamType())) {
                                   outputBuffer.append(logLine).append("\n");
                               }
                           }
                       }
                   })
                   .awaitCompletion(timeoutSeconds, TimeUnit.SECONDS);
           } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
               log.warn("Log streaming interrupted for container {}", containerId);
           }
       }
       
       /**
        * Monitor container logs asynchronously (non-blocking).
        */
       public void monitorAsync(DockerClient client, String pipelineId, String containerId) {
           CompletableFuture.runAsync(() -> {
               try {
                   client.logContainerCmd(containerId)
                       .withStdOut(true)
                       .withStdErr(true)
                       .withFollowStream(true)
                       .exec(new ResultCallback.Adapter<Frame>() {
                           @Override
                           public void onNext(Frame frame) {
                               String logLine = new String(frame.getPayload()).trim();
                               if (!logLine.isEmpty()) {
                                   logPublisher.publish(pipelineId, 
                                       "[" + containerId.substring(0, 8) + "] " + logLine);
                               }
                           }
                       })
                       .awaitCompletion();
               } catch (Exception e) {
                   log.warn("Stopped monitoring logs for container {}", containerId);
               }
           });
       }
   }
   ```

2. Update `ContainerExecutor`:
   - Add `ContainerLogStreamer` injection
   - Replace `streamLogs(...)` calls with `logStreamer.streamLogs(...)`
   - Replace `monitorContainerLogs(...)` calls with `logStreamer.monitorAsync(...)`
   - Remove the two streaming methods

#### Acceptance Criteria

- [ ] `ContainerLogStreamer` class created with `streamLogs()` and `monitorAsync()` methods
- [ ] `ContainerExecutor` injects and uses `ContainerLogStreamer`
- [ ] Duplicate Javadoc removed from `ContainerExecutor`
- [ ] `ContainerExecutor` reduced by ~50 lines
- [ ] Pipeline logs still stream to WebSocket clients
- [ ] Long-running containers show real-time logs

#### Testing

**Manual Testing**:
1. Start application
2. Trigger a pipeline
3. Connect to WebSocket and observe real-time logs
4. Verify logs appear for all steps (clone, install, build, etc.)
5. Verify APP_RUN logs stream continuously for running container

---

## Phase 2: Mid-Term (1-2 Months) - Architectural Improvements

**Goal**: Proper layer separation without breaking existing APIs.

---

### TICKET P2-01: Create Domain Model Package

**Priority**: High  
**Effort**: 1 week  
**Dependencies**: P1-01 through P1-06 (Phase 1 complete)  
**Assignee**: _TBD_

#### Description

Separate domain entities from persistence entities to achieve proper hexagonal architecture. Create a pure domain layer with no framework dependencies.

#### Current State

- `Pipeline`, `Project`, `Job` are MongoDB documents in `infrastructure.persistence`
- Port interfaces in `business.port` import infrastructure entities
- Dependency direction is inverted (domain → infrastructure)

#### Files to Create

```
src/main/java/fr/imt/deployzilla/deployzilla/domain/
├── model/
│   ├── Pipeline.java           # Domain aggregate (no @Document)
│   ├── Job.java                # Value object
│   ├── Project.java            # Domain entity
│   ├── JobType.java            # Move from business.model
│   └── DeploymentTarget.java   # New enum
├── port/
│   └── out/
│       ├── PipelineRepository.java     # Domain port
│       └── ProjectRepository.java      # Domain port
```

#### Files to Modify

| File | Changes |
|------|---------|
| `infrastructure/persistence/Pipeline.java` | Rename to `PipelineDocument.java` |
| `infrastructure/persistence/Project.java` | Rename to `ProjectDocument.java` |
| `infrastructure/persistence/Job.java` | Rename to `JobDocument.java` |
| All services using entities | Import domain models instead |
| All ports | Import domain models instead |
| All adapters | Add mapping logic |

#### Implementation Steps

1. Create domain model classes (no Lombok `@Data`, use immutability):
   ```java
   // Domain model - no framework annotations
   public final class Pipeline {
       private final String id;
       private final String projectId;
       private final List<Job> jobs;
       private PipelineStatus status;
       private final LocalDateTime createdAt;
       
       public Pipeline(String projectId) {
           this.id = UUID.randomUUID().toString();
           this.projectId = projectId;
           this.jobs = new ArrayList<>();
           this.status = PipelineStatus.CREATED;
           this.createdAt = LocalDateTime.now();
       }
       
       public void addJob(Job job) {
           this.jobs.add(job);
       }
       
       public void markRunning() {
           this.status = PipelineStatus.RUNNING;
       }
       
       // Domain validation
       public void validate() {
           if (jobs.isEmpty()) {
               throw new InvalidPipelineException("Pipeline must have at least one job");
           }
       }
   }
   ```

2. Create domain ports (in domain layer):
   ```java
   package fr.imt.deployzilla.deployzilla.domain.port.out;
   
   import fr.imt.deployzilla.deployzilla.domain.model.Pipeline;
   
   public interface PipelineRepository {
       Pipeline save(Pipeline pipeline);
       Optional<Pipeline> findById(String id);
   }
   ```

3. Rename persistence entities:
   - `Pipeline.java` → `PipelineDocument.java`
   - Keep `@Document` and MongoDB annotations

4. Create mappers:
   ```java
   @Mapper(componentModel = "spring")
   public interface PipelineMapper {
       Pipeline toDomain(PipelineDocument document);
       PipelineDocument toDocument(Pipeline domain);
   }
   ```

5. Update adapters:
   ```java
   @Component
   @RequiredArgsConstructor
   public class MongoPipelineAdapter implements PipelineRepository {
       private final PipelineMongoRepository mongoRepository;
       private final PipelineMapper mapper;
       
       @Override
       public Pipeline save(Pipeline pipeline) {
           PipelineDocument document = mapper.toDocument(pipeline);
           PipelineDocument saved = mongoRepository.save(document);
           return mapper.toDomain(saved);
       }
   }
   ```

6. Update all services to use domain models

#### Acceptance Criteria

- [ ] `domain.model` package contains `Pipeline`, `Job`, `Project` without framework annotations
- [ ] `domain.port.out` contains repository interfaces using domain types
- [ ] `infrastructure.persistence.entity` contains `*Document` classes with MongoDB annotations
- [ ] Mappers convert between domain and persistence models
- [ ] No imports from `infrastructure` in `domain` package
- [ ] All existing functionality still works
- [ ] Compile check: `domain` package has zero Spring/MongoDB imports

#### Testing

**Manual Testing**:
1. Start application
2. Create a project via API
3. Trigger a pipeline
4. Verify all steps complete successfully
5. Check MongoDB to verify data is stored correctly

**Verification Command**:
```bash
# Verify no infrastructure imports in domain
grep -r "import.*infrastructure" src/main/java/fr/imt/deployzilla/deployzilla/domain/
# Should return no results
```

---

### TICKET P2-02: Introduce MapStruct Mappers

**Priority**: Medium  
**Effort**: 3 days  
**Dependencies**: P2-01  
**Assignee**: _TBD_

#### Description

Add MapStruct mappers to convert between domain models and persistence documents, and between domain models and DTOs.

#### Files to Create

```
src/main/java/fr/imt/deployzilla/deployzilla/infrastructure/persistence/mapper/
├── PipelineDocumentMapper.java
├── ProjectDocumentMapper.java
└── JobDocumentMapper.java

src/main/java/fr/imt/deployzilla/deployzilla/presentation/web/mapper/
├── PipelineResponseMapper.java
└── ProjectResponseMapper.java
```

#### Implementation Details

MapStruct is already in `pom.xml`. Create mappers:

```java
@Mapper(componentModel = "spring", uses = {JobDocumentMapper.class})
public interface PipelineDocumentMapper {
    
    @Mapping(target = "id", source = "id")
    @Mapping(target = "status", source = "status", qualifiedByName = "statusToEnum")
    Pipeline toDomain(PipelineDocument document);
    
    @Mapping(target = "id", source = "id")
    @Mapping(target = "status", source = "status", qualifiedByName = "enumToStatus")
    PipelineDocument toDocument(Pipeline domain);
    
    @Named("statusToEnum")
    default PipelineStatus statusToEnum(String status) {
        return PipelineStatus.valueOf(status);
    }
    
    @Named("enumToStatus")
    default String enumToStatus(PipelineStatus status) {
        return status.name();
    }
}
```

#### Acceptance Criteria

- [ ] MapStruct mappers created for all domain ↔ document conversions
- [ ] MapStruct mappers created for all domain ↔ DTO conversions
- [ ] No manual mapping code in repository adapters
- [ ] All mapper methods tested with unit tests

---

### TICKET P2-03: Configuration-Driven Pipeline Jobs

**Priority**: High  
**Effort**: 1 week  
**Dependencies**: P2-01  
**Assignee**: _TBD_

#### Description

Replace hardcoded pipeline job sequence with YAML configuration, allowing different pipeline templates for different project types.

#### Current State

- `PipelineService.createPipeline()` hardcodes 8 jobs
- All projects execute the same pipeline regardless of type
- Cannot skip optional steps or customize order

#### Files to Create

| File | Purpose |
|------|---------|
| `src/.../configuration/PipelineConfiguration.java` | `@ConfigurationProperties` class |
| `src/.../domain/model/PipelineTemplate.java` | Template definition |
| `src/.../domain/model/JobDefinition.java` | Job configuration |
| `src/.../domain/service/PipelineFactory.java` | Creates pipelines from templates |

#### Configuration Format

```yaml
# application.yml
deployzilla:
  pipelines:
    templates:
      nodejs-default:
        name: "Node.js Default Pipeline"
        jobs:
          - type: CLONE
            required: true
          - type: NPM_INSTALL
            required: true
          - type: NPM_LINT
            required: false
            continueOnFailure: true
          - type: NPM_TEST
            required: false
            continueOnFailure: true
          - type: SONAR
            required: false
          - type: NPM_BUILD
            required: true
          - type: IMAGE_BUILD
            required: true
          - type: APP_RUN
            required: true
            
      nodejs-simple:
        name: "Node.js Simple (No Tests)"
        jobs:
          - type: CLONE
          - type: NPM_INSTALL
          - type: NPM_BUILD
          - type: IMAGE_BUILD
          - type: APP_RUN
```

#### Implementation Steps

1. Create configuration classes:
   ```java
   @ConfigurationProperties(prefix = "deployzilla.pipelines")
   @Validated
   public class PipelineConfiguration {
       @Valid
       @NotEmpty
       private Map<String, PipelineTemplateConfig> templates;
   }
   
   public class PipelineTemplateConfig {
       @NotBlank
       private String name;
       
       @NotEmpty
       private List<JobConfig> jobs;
   }
   
   public class JobConfig {
       @NotNull
       private JobType type;
       private boolean required = true;
       private boolean continueOnFailure = false;
   }
   ```

2. Create `PipelineFactory`:
   ```java
   @Service
   @RequiredArgsConstructor
   public class PipelineFactory {
       private final PipelineConfiguration config;
       
       public Pipeline createFromTemplate(String templateName, String projectId) {
           PipelineTemplateConfig template = config.getTemplates().get(templateName);
           if (template == null) {
               throw new UnknownPipelineTemplateException(templateName);
           }
           
           Pipeline pipeline = new Pipeline(projectId);
           for (JobConfig jobConfig : template.getJobs()) {
               pipeline.addJob(new Job(
                   jobConfig.getType(),
                   jobConfig.isRequired(),
                   jobConfig.isContinueOnFailure()
               ));
           }
           return pipeline;
       }
   }
   ```

3. Update `PipelineService`:
   ```java
   public Pipeline createPipeline(String projectId, String templateName, ...) {
       Pipeline pipeline = pipelineFactory.createFromTemplate(
           templateName != null ? templateName : "nodejs-default",
           projectId
       );
       // Rest of creation logic
   }
   ```

4. Update `Project` entity to store preferred template:
   ```java
   private String pipelineTemplate = "nodejs-default";
   ```

#### Acceptance Criteria

- [ ] `PipelineConfiguration` loads templates from `application.yml`
- [ ] At least 2 pipeline templates defined (default, simple)
- [ ] `PipelineFactory.createFromTemplate()` creates pipeline from config
- [ ] Optional jobs can be skipped
- [ ] `continueOnFailure` jobs don't break pipeline
- [ ] API accepts `template` parameter in pipeline creation
- [ ] Invalid template name returns 400 Bad Request

#### Testing

**Manual Testing**:
1. Trigger pipeline with default template → 8 jobs execute
2. Trigger pipeline with `nodejs-simple` template → 5 jobs execute
3. Trigger pipeline with invalid template → 400 error
4. Configure a job with `continueOnFailure: true`, make it fail → Pipeline continues

---

### TICKET P2-04: Replace `sshpass` with JSch Library

**Priority**: High  
**Effort**: 3 days  
**Dependencies**: P1-01 (SshTunnelManager extracted)  
**Assignee**: _TBD_

#### Description

Replace the external `sshpass` CLI tool with the pure Java JSch library for more secure and reliable SSH tunnel management.

#### Current Issues

- Requires `sshpass` installed on host (platform dependency)
- Password visible in process list (`ps aux`)
- No automatic reconnection on tunnel failure
- `Thread.sleep(3000)` for "stabilization" is fragile

#### Files to Create

| File | Purpose |
|------|---------|
| `src/.../infrastructure/ssh/JschSshTunnel.java` | JSch-based implementation |

#### Files to Modify

| File | Changes |
|------|---------|
| `pom.xml` | Add JSch dependency |
| `src/.../infrastructure/ssh/SshpassTunnelManager.java` | Keep as fallback (optional) |
| `src/.../configuration/SshConfiguration.java` | Add `implementation` choice |

#### Implementation Steps

1. Add JSch dependency:
   ```xml
   <dependency>
       <groupId>com.jcraft</groupId>
       <artifactId>jsch</artifactId>
       <version>0.1.55</version>
   </dependency>
   ```

2. Implement `JschSshTunnel`:
   ```java
   @Service
   @Slf4j
   public class JschSshTunnel implements SshTunnel {
       
       private final JSch jsch = new JSch();
       private Session session;
       
       @Value("${deployzilla.remote.host}")
       private String host;
       
       @Value("${deployzilla.remote.user}")
       private String user;
       
       @Value("${deployzilla.remote.password}")
       private String password;
       
       @Value("${deployzilla.remote.port:22}")
       private int sshPort;
       
       private static final int LOCAL_PORT = 2375;
       private static final int REMOTE_DOCKER_PORT = 2375;
       
       @Override
       public void connect() throws SshException {
           try {
               log.info("Connecting to SSH: {}@{}:{}", user, host, sshPort);
               
               session = jsch.getSession(user, host, sshPort);
               session.setPassword(password);
               
               // Disable strict host key checking (same as current behavior)
               java.util.Properties config = new java.util.Properties();
               config.put("StrictHostKeyChecking", "no");
               session.setConfig(config);
               
               session.connect(30_000); // 30 second timeout
               
               // Set up port forwarding: local:2375 -> remote:/var/run/docker.sock
               session.setPortForwardingL(LOCAL_PORT, "localhost", REMOTE_DOCKER_PORT);
               
               log.info("SSH tunnel established on port {}", LOCAL_PORT);
               
           } catch (JSchException e) {
               throw new SshException("Failed to establish SSH tunnel to " + host, e);
           }
       }
       
       @Override
       public void disconnect() {
           if (session != null && session.isConnected()) {
               session.disconnect();
               log.info("SSH tunnel disconnected");
           }
       }
       
       @Override
       public boolean isConnected() {
           return session != null && session.isConnected();
       }
       
       @Override
       public int getLocalPort() {
           return LOCAL_PORT;
       }
       
       /**
        * Reconnect if connection was lost.
        */
       public void ensureConnected() throws SshException {
           if (!isConnected()) {
               log.warn("SSH tunnel disconnected, reconnecting...");
               connect();
           }
       }
   }
   ```

3. Update configuration to choose implementation:
   ```yaml
   deployzilla:
     remote:
       enabled: true
       implementation: jsch  # or: sshpass (fallback)
       host: "147.79.114.156"
       user: ${DEPLOYZILLA_REMOTE_USER}
       password: ${DEPLOYZILLA_REMOTE_PASSWORD}
       port: ${DEPLOYZILLA_REMOTE_PORT}
   ```

4. Add conditional bean:
   ```java
   @Configuration
   public class SshConfiguration {
       
       @Bean
       @ConditionalOnProperty(name = "deployzilla.remote.implementation", havingValue = "jsch", matchIfMissing = true)
       public SshTunnel jschSshTunnel() {
           return new JschSshTunnel();
       }
       
       @Bean
       @ConditionalOnProperty(name = "deployzilla.remote.implementation", havingValue = "sshpass")
       public SshTunnel sshpassTunnel() {
           return new SshpassTunnelManager();
       }
   }
   ```

#### Acceptance Criteria

- [ ] JSch dependency added
- [ ] `JschSshTunnel` implementation created
- [ ] No external `sshpass` dependency required (when using jsch)
- [ ] Password not visible in process list
- [ ] Automatic reconnection on tunnel failure
- [ ] Configuration allows choosing implementation
- [ ] Remove `Thread.sleep(3000)` (JSch provides proper connection confirmation)
- [ ] All remote deployment tests pass

#### Testing

**Manual Testing**:
1. Remove `sshpass` from Docker image / local machine
2. Configure `implementation: jsch`
3. Start application → SSH tunnel should connect
4. Trigger pipeline → Remote deployment should work
5. Kill SSH session manually → Application should reconnect

---

### TICKET P2-05: Add OpenAPI Documentation

**Priority**: Low  
**Effort**: 1 day  
**Dependencies**: None  
**Assignee**: _TBD_

#### Description

Add SpringDoc OpenAPI for automatic API documentation generation.

#### Implementation Steps

1. Add dependency:
   ```xml
   <dependency>
       <groupId>org.springdoc</groupId>
       <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
       <version>2.3.0</version>
   </dependency>
   ```

2. Add OpenAPI configuration:
   ```java
   @Configuration
   public class OpenApiConfiguration {
       @Bean
       public OpenAPI deployzillaOpenAPI() {
           return new OpenAPI()
               .info(new Info()
                   .title("Deployzilla API")
                   .description("Docker deployment orchestrator")
                   .version("1.0.0"));
       }
   }
   ```

3. Add annotations to controllers:
   ```java
   @Operation(summary = "Start a new pipeline")
   @ApiResponse(responseCode = "200", description = "Pipeline started successfully")
   @PostMapping("/start")
   public ResponseEntity<PipelineResponse> startPipeline(...) { }
   ```

#### Acceptance Criteria

- [ ] Swagger UI accessible at `/swagger-ui.html`
- [ ] All endpoints documented with descriptions
- [ ] Request/response schemas visible
- [ ] API can be tested from Swagger UI

---

### TICKET P2-06: Implement Integration Tests

**Priority**: High  
**Effort**: 1 week  
**Dependencies**: P2-01, P2-02  
**Assignee**: _TBD_

#### Description

Add integration tests using Testcontainers for MongoDB, Redis, and Docker-in-Docker testing.

#### Files to Create

```
src/test/java/fr/imt/deployzilla/deployzilla/integration/
├── PipelineIntegrationTest.java
├── DockerOperationsTest.java
└── TestcontainersConfiguration.java
```

#### Implementation Steps

1. Add test dependencies:
   ```xml
   <dependency>
       <groupId>org.testcontainers</groupId>
       <artifactId>testcontainers</artifactId>
       <scope>test</scope>
   </dependency>
   <dependency>
       <groupId>org.testcontainers</groupId>
       <artifactId>mongodb</artifactId>
       <scope>test</scope>
   </dependency>
   <dependency>
       <groupId>org.testcontainers</groupId>
       <artifactId>junit-jupiter</artifactId>
       <scope>test</scope>
   </dependency>
   ```

2. Create test configuration:
   ```java
   @Testcontainers
   @SpringBootTest
   public class PipelineIntegrationTest {
       
       @Container
       static MongoDBContainer mongo = new MongoDBContainer("mongo:7");
       
       @Container
       static GenericContainer<?> redis = new GenericContainer<>("redis:7")
           .withExposedPorts(6379);
       
       @DynamicPropertySource
       static void configureProperties(DynamicPropertyRegistry registry) {
           registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
           registry.add("spring.data.redis.host", redis::getHost);
           registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
       }
       
       @Test
       void shouldCreateAndRetrievePipeline() {
           // Test implementation
       }
   }
   ```

#### Acceptance Criteria

- [ ] Testcontainers configured for MongoDB and Redis
- [ ] At least 5 integration tests covering pipeline lifecycle
- [ ] Tests run in CI/CD pipeline
- [ ] Test coverage report generated

---

## Phase 3: Long-Term (3-6 Months) - Deeper Redesigns

### TICKET P3-01: Event-Driven Pipeline Execution

**Priority**: High  
**Effort**: 2 weeks  
**Dependencies**: Phase 2 complete  

*Full details to be defined after Phase 2 completion*

---

### TICKET P3-02: Pluggable Deployment Targets

**Priority**: High  
**Effort**: 2 weeks  
**Dependencies**: P3-01  

*Full details to be defined after Phase 2 completion*

---

### TICKET P3-03: Kubernetes Deployment Support

**Priority**: Medium  
**Effort**: 3 weeks  
**Dependencies**: P3-02  

*Full details to be defined after Phase 2 completion*

---

### TICKET P3-04: External Secrets Management

**Priority**: Medium  
**Effort**: 1 week  
**Dependencies**: None  

*Full details to be defined after Phase 2 completion*

---

### TICKET P3-05: Distributed Job Execution

**Priority**: Low  
**Effort**: 1 month  
**Dependencies**: P3-01  

*Full details to be defined after Phase 2 completion*

---

## Migration Checklist

### Before Starting

- [ ] All tests pass (run `mvn test`)
- [ ] Create feature branch from `main`
- [ ] Set up CI pipeline for new branch
- [ ] Document current behavior

### Phase 1 Completion Criteria

- [ ] `ContainerExecutor` < 300 lines (down from 597)
- [ ] No hardcoded credentials in config files
- [ ] Domain exceptions in use everywhere
- [ ] Spring Retry configured for transient failures
- [ ] All 6 P1 tickets completed

### Phase 2 Completion Criteria

- [ ] Domain and infrastructure layers fully separated
- [ ] MapStruct mappers handle all conversions
- [ ] JSch replacing sshpass
- [ ] OpenAPI docs available at `/swagger-ui.html`
- [ ] Integration test coverage > 70%
- [ ] All 6 P2 tickets completed

### Phase 3 Completion Criteria

- [ ] Event-driven execution working
- [ ] At least 2 deployment targets supported
- [ ] Secrets externalized
- [ ] Load testing passed (10 concurrent pipelines)
- [ ] All 5 P3 tickets completed

---

## Risk Mitigation

| Change | Risk | Mitigation |
|--------|------|------------|
| Domain layer extraction | Breaking persistence | Feature flags, parallel models during migration |
| SSH library swap | Connection issues | Keep sshpass as fallback, extensive testing |
| Event-driven migration | State inconsistency | Saga pattern, idempotent handlers |
| Kubernetes support | Complexity | Phased rollout, local minikube testing first |

---

*Last Updated: January 2026*
