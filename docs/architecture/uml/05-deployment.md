# Deployment (AWS)

How the system is meant to run. **Nothing here exists yet**: there is no infrastructure code and
the applications run only locally (`./mvnw spring-boot:run`, `npm run dev`). The services and
their names come from `CLAUDE.md`; the network layout is an assumption (see the
[README](README.md)).

```mermaid
flowchart TB
    user["Clinic staff browser"]
    llm["External LLM provider"]

    subgraph aws["AWS account, one region"]
        cf["CloudFront distribution"]
        s3["S3 bucket<br/>petclinic-ui build output"]

        subgraph vpc["VPC"]
            subgraph pub["Public subnets, two AZs"]
                alb["Application Load Balancer<br/>/petclinic/* and /agent/*"]
                nat["NAT gateway"]
            end

            subgraph priv["Private subnets, two AZs"]
                subgraph cluster["ECS cluster, Fargate"]
                    apisvc["Service: spring-petclinic-rest<br/>port 9966, context path /petclinic"]
                    agentsvc["Service: petclinic-ai-agent"]
                end
            end

            subgraph data["Private data subnets"]
                rds[("RDS PostgreSQL<br/>Multi-AZ")]
            end
        end

        ecr["ECR<br/>container images"]
        sm["Secrets Manager"]
        ps["Parameter Store"]
        cw["CloudWatch<br/>logs, metrics, alarms"]
    end

    user -->|HTTPS| cf
    cf -->|"static"| s3
    cf -->|"/petclinic/*, /agent/*"| alb
    alb --> apisvc
    alb --> agentsvc
    agentsvc -->|"REST, internal"| apisvc
    apisvc -->|"JDBC"| rds
    agentsvc -->|"egress"| nat
    nat --> llm
    apisvc -.-> sm
    apisvc -.-> ps
    agentsvc -.-> sm
    agentsvc -.-> ps
    apisvc -.->|"logs"| cw
    agentsvc -.->|"logs"| cw
    ecr -.->|"image pull"| cluster

    classDef target stroke-dasharray: 5 5
    class cf,s3,alb,nat,apisvc,agentsvc,rds,ecr,sm,ps,cw,llm target
```

## Elements

| Element | Purpose | Notes |
|---|---|---|
| CloudFront + S3 | Serve the React build; single public entry point | S3 stays private, reached through origin access control |
| ALB | Path-based routing to the two services | Only public component in front of the tasks |
| ECS Fargate services | One service per application, scaled independently ([ADR-0002](../../adr/0002-separate-ai-agent-from-ui-and-rest-api.md)) | Images built with Jib (already configured for the API) or a Dockerfile |
| RDS PostgreSQL | System of record | Reachable from the API's security group only, not the agent's |
| Secrets Manager | DB password, LLM API key, token signing keys | Injected as environment variables by the execution role |
| Parameter Store | Non-secret config such as `petclinic.security.enable`, feature switches | |
| CloudWatch | Logs, metrics from Actuator, alarms | |

## Things to settle before building

- The agent's internal call to the API goes through the ALB in this diagram; ECS Service Connect
  or an internal ALB would keep it off the public path.
- Deployed configuration must set `petclinic.security.enable=true` and must not use the in-memory
  `h2` profile, which reloads seed data on every restart.
- Agent and API must be rolled out together when a REST contract changes, or the API versioned.
