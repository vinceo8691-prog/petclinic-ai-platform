# PetClinic AI Platform

## Architecture

The system consists of three primary applications:

- spring-petclinic-rest — Spring Boot REST backend
- petclinic-ui — React/TypeScript frontend
- petclinic-ai-agent — Spring Boot AI agent

## Architecture Rules

- The AI Agent never accesses the PetClinic database directly.
- PetClinic owns business data.
- The AI Agent accesses PetClinic through REST APIs.
- React communicates with both PetClinic and the AI Agent.
- AI-initiated writes require human confirmation.

## Technology

Backend:
- Java
- Spring Boot
- Spring Data JPA

Frontend:
- React
- TypeScript

Infrastructure:
- AWS
- ECS/Fargate
- RDS
- CloudFront/S3
- Secrets Manager
- Parameter Store
- CloudWatch

## Development Guidelines

- Prefer straightforward architecture over unnecessary abstraction.
- Explain significant architectural changes before implementing them.
- Add tests for new functionality.
- Do not introduce new dependencies without explaining why.
- Explicitly call out security implications when a change touches a security-related subject (authentication, authorization, secrets, etc.). 
- In all AI-generated code, do not use single character variable, property and parameter names, except for loop indices and parameters of short inline lambdas and callbacks. Use descriptive names instead
