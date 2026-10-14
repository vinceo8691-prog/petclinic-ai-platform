# Components

Responsibilities and dependencies of the major services. Dashed = target, not built yet.

## Platform level

```mermaid
flowchart LR
    subgraph ui["petclinic-ui"]
        pages["Pages<br/>Owners, Add/Edit owner, Owner details"]
        query["TanStack Query data layer"]
        panel["Assistant panel<br/>preview only, not connected"]
        confirm["Proposal review and confirm<br/>executes the write itself"]
    end

    subgraph api["spring-petclinic-rest"]
        controllers["Controllers v1 and v2<br/>generated OpenAPI interfaces"]
        sec["Spring Security<br/>method security, role hierarchy"]
        services["Services<br/>Owner, Pet, Visit, Vet, PetType, Specialty, User"]
        repos["Spring Data JPA repositories"]
        advice["ExceptionControllerAdvice<br/>ProblemDetail 400 / 403 / 404 / 409 / 500"]
        issuer["Token issuer<br/>login, token exchange, delegated token"]
    end

    subgraph agent["petclinic-ai-agent"]
        chat["Chat endpoint"]
        orchestrator["Agent orchestrator<br/>prompt, tool loop"]
        tools["Tool adapters<br/>one per PetClinic REST operation"]
        proposals["Proposal builder<br/>structured change, never executed"]
    end

    db[("Database")]
    llm["External LLM provider"]

    pages --> query --> controllers
    panel -.-> chat
    confirm -.->|"confirmed write, employee token"| controllers
    pages -.->|"login"| issuer
    pages -.->|"exchange for delegated token"| issuer
    controllers --> sec
    controllers --> services --> repos --> db
    controllers --> advice
    chat -.-> orchestrator
    orchestrator -.-> llm
    orchestrator -.-> tools
    orchestrator -.-> proposals
    tools -.->|"REST reads, delegated read-only token"| controllers

    classDef target stroke-dasharray: 5 5
    class agent,chat,orchestrator,tools,proposals,confirm,llm,issuer target
```

## Responsibilities

| Component | Responsible for | Depends on | Must not |
|---|---|---|---|
| `petclinic-ui` | Screens, navigation, search/sort/paging state, rendering AI proposals, executing a confirmed write | REST API, agent | Hold model credentials or call the LLM |
| Controllers | HTTP mapping, input validation, `@PreAuthorize` per method | Services, generated DTOs, MapStruct mappers | Contain business rules |
| Services | Business rules per aggregate (`PetService` is the only one that calls another service, `PetTypeService`) | Repositories | Be reached by the agent except through a controller |
| Repositories | Persistence, including the case-insensitive owner prefix search | Database | |
| `ExceptionControllerAdvice` | Mapping errors to RFC 7807 `ProblemDetail` | | Leak stack traces |
| `petclinic-ai-agent` | Conversation, read-tool selection, turning a model decision into a structured proposal | LLM provider, REST API (as the employee) | Connect to the database; write at all; keep state across users |

## Backend layering (built)

```mermaid
flowchart LR
    controllers["Controllers v1, v2"] --> services["Services"] --> repositories["Repositories"] --> entities["JPA entities"]
    controllers --> mappers["MapStruct mappers<br/>entity and DTO"]
```
