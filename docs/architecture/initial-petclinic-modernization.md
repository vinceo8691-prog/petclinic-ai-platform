# Initial modernization: collapsing Service interfaces into single classes

This recounts a change already in place before this platform's work began — it
predates the ClinicService → per-aggregate services split and the ADRs in
`../adr`. It's documented here because the codebase no longer shows the
"before" state, and future readers comparing against the original Spring
PetClinic project will otherwise find the difference confusing.

## Before: interface + impl, per service

The original Spring PetClinic REST project (`spring-petclinic/spring-petclinic-rest`,
wired as the `upstream` remote in this repo) follows the classic Spring
service-layer pattern: one interface declaring the contract, one `*Impl` class
providing it, wired together with field-level `@Autowired`.

For `UserService`, that looked like:

```java
// UserService.java (interface)
public interface UserService {
    void saveUser(User user);
}

// UserServiceImpl.java
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Override
    @Transactional
    public void saveUser(User user) {
        // ...
    }
}
```

`ClinicService` followed the same shape: a `ClinicService` interface listing
every method, and a `ClinicServiceImpl` implementing it against six
repositories.

In both cases, exactly one implementation of the interface ever existed. The
interface added a second file and a layer of indirection (jump-to-definition
lands on the interface, not the logic) without ever being used for
polymorphism, multiple implementations, or test doubles that needed an
interface specifically — Spring can inject and proxy concrete classes directly,
and Mockito mocks concrete classes just as easily as interfaces.

## The change: one concrete class, no interface

By the time this platform's work started, both services had been collapsed
into a single concrete class each — `ClinicService` and `UserService` — with
no interface and no `*Impl` suffix anywhere. The collapse also replaced
field-level `@Autowired` with constructor injection:

```java
// UserService.java — the whole thing
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void saveUser(User user) {
        // ...
    }
}
```

Same idea for `ClinicService`: one `@Service` class, constructor-injected
repositories, no interface to maintain in parallel with the implementation.

## Why this is a reasonable simplification

An interface with exactly one implementation and no test-double need is pure
ceremony: it doubles the files to maintain for every method signature change,
and adds a hop for anyone navigating the code. Collapsing to a single class
removes that ceremony without losing anything — Spring's dependency injection
and Mockito's mocking both work the same way against a concrete class.

(The rationale above is inferred from the diff between upstream and this fork,
not from a recorded decision — if the actual motivation was different, treat
this section as the "why it's defensible" case rather than the historical
record.)

## What happened next

`UserService` is still a single class today, unchanged. `ClinicService` was
not — it had grown into a god object wrapping all six repositories (Pet, Vet,
Owner, Visit, Specialty, PetType) behind one facade injected into every
controller. That class was later split into six per-aggregate services
(`OwnerService`, `PetService`, `VisitService`, `VetService`, `PetTypeService`,
`SpecialtyService`), each still a single concrete class with no interface —
the "one class, no interface" pattern from this change was kept, just applied
per aggregate instead of to one facade covering all of them.
