# System context

Who uses the platform and which systems it depends on. Dashed = target, not built yet.

```mermaid
flowchart TB
    staff["Clinic staff<br/>ROLE_OWNER_ADMIN, ROLE_VET_ADMIN, ROLE_ADMIN"]

    subgraph platform["PetClinic AI Platform"]
        ui["petclinic-ui<br/>React / TypeScript SPA"]
        api["spring-petclinic-rest<br/>Spring Boot REST API<br/>owns all business data"]
        agent["petclinic-ai-agent<br/>Spring Boot AI agent"]
    end

    db[("Relational database<br/>H2 locally, RDS on AWS")]
    llm["External LLM provider"]
    aws["AWS platform services<br/>Secrets Manager, Parameter Store, CloudWatch"]

    staff -->|"browser, HTTPS"| ui
    ui -->|"REST /api"| api
    ui -.->|"chat, confirm writes"| agent
    agent -.->|"REST only, never the database"| api
    api -->|"JPA"| db
    agent -.->|"prompts and tool calls"| llm
    api -.->|"config, secrets, logs"| aws
    agent -.->|"config, secrets, logs"| aws

    classDef target stroke-dasharray: 5 5
    class agent,llm,aws target
```

## Notes

- **The agent never touches the database.** It is an ordinary authenticated REST client of
  `spring-petclinic-rest` ([ADR-0002](../../adr/0002-separate-ai-agent-from-ui-and-rest-api.md)).
- **PetClinic owns business data.** The agent holds no persistent copy of owners, pets or visits.
- **The UI talks to both backends directly** and is where human confirmation of AI-initiated
  writes is surfaced.
- The UI-to-agent edge, the agent and everything on AWS are target state; today the UI calls only
  the REST API and runs locally.
