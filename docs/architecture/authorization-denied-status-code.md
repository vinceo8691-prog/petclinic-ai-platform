# Fixing the status code for denied @PreAuthorize checks

This recounts a bug found while verifying [ADR-0006](../adr/0006-admin-role-hierarchy.md)
(the ROLE_ADMIN hierarchy) and the fix for it. It's not an architectural
decision — there was never a real alternative to weigh, just a wrong status
code to correct — so it lives here instead of in `../adr`.

## The bug: a denied authorization check returned 500, not 403

A principal lacking the role required by a controller's `@PreAuthorize`
check should get back a `403 Forbidden`. Instead, every denied request was
returning `500 Internal Server Error`:

```json
{
  "detail": "An unexpected error occurred while processing your request",
  "title": "AuthorizationDeniedException",
  "status": 500
}
```

The first hypothesis — that this was an artifact of the controller unit
tests using `MockMvcBuilders.standaloneSetup(...)`, which builds a bare MVC
dispatcher with none of Spring Security's filters wrapped around it — turned
out to be wrong. A test built against the real filter chain
(`MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())`,
in `AuthorizationFailureStatusTests`) reproduced the exact same 500. This is
a real bug in the deployed application, not a test-setup gap.

## Why it happened

Spring Security's `AuthorizationDeniedException` (thrown by `@PreAuthorize`
when a check fails) extends `AccessDeniedException`. Normally, Spring
Security's `ExceptionTranslationFilter` — part of the security filter chain
— catches `AccessDeniedException` and translates it to a 403 response via an
`AccessDeniedHandler`, with no application code required.

That translation only happens for exceptions that escape the servlet
entirely. `ExceptionControllerAdvice` had a catch-all
`@ExceptionHandler(Exception.class)` handler, and Spring MVC resolves
exceptions thrown during request handling — including ones thrown by a
method-security AOP interceptor wrapping the controller method — through its
own `HandlerExceptionResolver` chain *inside* `DispatcherServlet`, before
anything could propagate back out to the surrounding filter chain. The
catch-all handler matched first, turned the exception into a normal
(if wrong) HTTP response, and `ExceptionTranslationFilter` never got a
chance to see it.

In short: `ExceptionControllerAdvice`'s own broad exception handling is what
prevented Spring Security's default, correct behavior from ever running.

## The fix

Add a handler specific to `AccessDeniedException` that returns 403, so it's
selected instead of the generic handler (Spring MVC matches the most
specific exception type, not declaration order):

```java
@ExceptionHandler(AccessDeniedException.class)
@ResponseBody
public ResponseEntity<ProblemDetail> handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request) {
    HttpStatus status = HttpStatus.FORBIDDEN;
    ProblemDetail detail = this.detailBuild(e, status, request.getRequestURL(), ERROR_ACCESS_DENIED);
    return ResponseEntity.status(status).body(detail);
}
```

`401 Unauthorized` (missing/invalid credentials, as opposed to valid
credentials with insufficient permission) was not affected by this bug and
needed no fix — that case is rejected by `BasicAuthenticationFilter`/
`ExceptionTranslationFilter` before the request ever reaches
`DispatcherServlet`, so it was never at risk of being caught by this
controller advice.

## Verification

- `AuthorizationFailureStatusTests` (new) exercises the real filter chain end
  to end and asserts 403 for a denied check — this is the test that first
  reproduced the bug, then confirmed the fix.
- `RoleHierarchyTests.ownerAdminIsStillDeniedTheVetAdminGatedEndpoint` was
  updated from asserting the old (wrong) 500 to asserting 403.
- Full suite (15 test classes) passes.
