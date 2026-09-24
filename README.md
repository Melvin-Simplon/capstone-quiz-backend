# Azure Quiz Backend

[![CI - Build module](https://github.com/Melvin-Simplon/capstone-quiz-backend/actions/workflows/ci-build.yml/badge.svg?branch=main)](https://github.com/Melvin-Simplon/capstone-quiz-backend/actions/workflows/ci-build.yml)
[![CI - Security scan](https://github.com/Melvin-Simplon/capstone-quiz-backend/actions/workflows/ci-security.yml/badge.svg?branch=main)](https://github.com/Melvin-Simplon/capstone-quiz-backend/actions/workflows/ci-security.yml)
[![CD - Deploy](https://github.com/Melvin-Simplon/capstone-quiz-backend/actions/workflows/cd-deploy.yml/badge.svg?branch=main)](https://github.com/Melvin-Simplon/capstone-quiz-backend/actions/workflows/cd-deploy.yml)
[![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)](pom.xml)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)](pom.xml)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](src/main/resources/db/migration)
[![Redis](https://img.shields.io/badge/cache-Redis-DC382D?logo=redis&logoColor=white)](src/main/java/com/alderichoarau/azurequiz/config)
[![Deploy](https://img.shields.io/badge/deploy-OIDC%2C%20no%20stored%20secret-2EA043)](#deployment)

REST API for the Microsoft Azure certification revision application. AZ-900 to start; another
certification such as AZ-104 can be added without a schema change, see [Data model](#data-model).

| | |
| --- | --- |
| Health | <https://app-simplon-quiz-mpetit.azurewebsites.net/actuator/health> |
| Swagger, locally | <http://localhost:8080/swagger-ui.html> |
| Infrastructure | [simplon-quiz-infrastructure-bilan](https://github.com/WhiteMuush/simplon-quiz-infrastructure-bilan) |
| Frontend | [simplon-quiz-frontend-bilan](https://github.com/WhiteMuush/simplon-quiz-frontend-bilan) |

---

## Stack

- Java 21, Spring Boot 3.5.x, Maven
- Spring Web, Spring Data JPA, PostgreSQL, Flyway, Bean Validation, Lombok, Actuator
- Spring Data Redis for the cache, Spring Cloud Azure Storage Blob for the result export
- springdoc-openapi for the Swagger UI
- JUnit 5, Mockito, AssertJ

## Running locally

Prerequisites: JDK 21, and Docker running.

```bash
./mvnw spring-boot:run
```

That is the whole of it. The `spring-boot-docker-compose` dependency finds `docker-compose.yml` at
the project root and starts PostgreSQL, Redis and Azurite before the application context loads,
then stops them with the application. It is marked `optional`, so it never ships in the jar
deployed to App Service.

To keep the containers running across restarts instead, the manual route still works:

```bash
docker compose up -d
./mvnw spring-boot:run
```

```bash
./mvnw test
```

## Content and behaviour worth knowing

**Migrations carry the questions.** Flyway applies `src/main/resources/db/migration` on startup,
including AZ-900 modules 1 to 6 (`V2` to `V7`), 45 questions each: 30 standard and 15 scenario
questions. The standard ones come from the trainer's answer key. The scenario ones have no written
key, since the trainer corrects them live, so their answers were derived from AZ-900 fundamentals
and deserve a review before being used in training.

One inconsistency was found in the trainer's key and corrected on import: module 1 question 8
marked SaaS as the answer while its own explanation describes PaaS. PaaS was imported.

**Mock exams stay separate.** Six official mock exams (`V9` to `V14`, 50 questions each) are
imported as modules of type `MOCK_EXAM`. The random exam mode draws only from modules of type
`CONTENT`, so an exam never leaks into revision and the reverse.

**The cache holds what migrations change.** `CertificationService.getAllCertifications()` and
`ModuleService.getModulesByCertification()` are `@Cacheable`, values JSON serialized rather than
JDK serialized, see `CacheConfig`. Entries expire after 30 minutes and nothing evicts them on
write, because the underlying data only ever changes through a new migration, never through the
running application.

**Exporting a result never breaks a quiz.** Every call to `GET /api/quiz-sessions/{id}/result` also
writes that result to blob storage, downloadable again from `.../result/export`. Locally it goes to
Azurite; in Azure, to the `java-uploads-mpetit` container, authenticated with the web app's managed
identity, no account key involved either way. A storage outage is logged and swallowed: PostgreSQL
remains the source of truth.

## Data model

`certification` to `module` to `question` to `answer_option`. A `quiz_session` is tied to a
certification, and in review mode to one module; in exam mode its questions are drawn at random
from every active module of that certification.

Adding a certification takes no schema migration: a row in `certification`, its modules and its
questions, in a dedicated Flyway migration.

## API contract

| Endpoint | Does |
| --- | --- |
| `GET /api/certifications` | lists the available certifications |
| `GET /api/certifications/{id}/modules` | modules of a certification, with active question count and `type` |
| `POST /api/quiz-sessions` | creates a session |
| `POST /api/quiz-sessions/{id}/questions/{questionId}/answer` | submits an answer, returns correctness, the correct options and the explanation |
| `GET /api/quiz-sessions/{id}/result` | aggregated score, and exports it as a blob |
| `GET /api/quiz-sessions/{id}/result/export` | downloads that blob, 404 if `result` was never called |

Creating a session takes either mode:

```jsonc
{ "mode": "MODULE", "moduleId": "...", "questionCount": 10 }   // questionCount optional
{ "mode": "EXAM", "certificationId": "...", "questionCount": 40 }  // defaults to 40
```

The response carries the questions and their options **without** marking the correct answer.

Everything under `/api/**` requires the `X-Api-Key` header when `BACKEND_API_KEY` is set, which it
is in Azure. `/actuator/health` deliberately sits outside that filter, since App Service's health
probe cannot send a header.

`/api/**` is also rate limited per calling address, 120 requests a minute and 20 writes, answering
`429` with a `Retry-After` beyond that. The application has no accounts and its key ships inside
the frontend bundle, so this is what stands between a loop on session creation and a full database.
Writes are capped harder because they are the ones that cost something: a row in PostgreSQL, and a
blob per exported result. The limits are configurable under `app.rate-limit`.

## Configuration in Azure

Terraform sets every one of these on the web app, three of them as Key Vault references, so none of
them exists in this repository.

| Variable | Holds |
| --- | --- |
| `SPRING_DATASOURCE_URL` | JDBC URL of the PostgreSQL Flexible Server, reached over the private network |
| `SPRING_DATASOURCE_USERNAME` | database user |
| `SPRING_DATASOURCE_PASSWORD` | Key Vault reference |
| `APP_CORS_ALLOWED_ORIGINS` | the static site's exact origin, and nothing else |
| `REDIS_HOSTNAME`, `REDIS_PORT` | Azure Managed Redis, reached through a private endpoint |
| `REDIS_PASSWORD` | Key Vault reference |
| `REDIS_SSL_ENABLED` | `true` in Azure, `false` locally |
| `BACKEND_API_KEY` | Key Vault reference. Unset locally, which switches the filter off |
| `STORAGE_ACCOUNT_NAME` | storage account, reached with the managed identity, no key |
| `STORAGE_CONTAINER_NAME` | `java-uploads-mpetit` |
| `SPRING_PROFILES_ACTIVE` | `prod`, which keeps the local-only settings from loading |

## Deployment

Every pull request runs [`ci-build.yml`](.github/workflows/ci-build.yml) (compile, tests,
SonarCloud) and [`ci-security.yml`](.github/workflows/ci-security.yml) (Trivy, dependency-review,
CodeQL, secret scanning). Merging into `main` runs [`cd-deploy.yml`](.github/workflows/cd-deploy.yml):
the build again for its jar, CodeQL for the reference, then the deployment, then a DAST scan of the
API. [`cd-dast.yml`](.github/workflows/cd-dast.yml) also scans the running API once a day.

Two properties worth stating:

- **The jar deployed is the jar the tests ran against.** It travels between jobs as an artifact
  rather than being rebuilt, so nothing untested ever reaches Azure.
- **The run only passes once `/actuator/health` answers `UP`.** A deployment can report success
  while the application fails to start, and that is the failure worth catching.

No credential is stored. The workflow proves which repository, branch and environment it runs from
over OIDC, and finds the web app by its `component` tag rather than by a name written down here.

## Out of scope

Provisioning the Azure resources, which lives in the infrastructure repository, and importing the
question content, supplied separately and converted into migrations.
