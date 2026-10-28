package spike.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Shared pieces for the attack tests: a clock the test can move, a grant store that can fail, and a local-key decoder. */
final class AttackTestSupport {

    static final TestTokens TOKENS = new TestTokens();
    static final MutableClock CLOCK = new MutableClock();
    static final FailingGrantStore STORE = new FailingGrantStore(new InMemoryGrantStore());

    private AttackTestSupport() {
    }

    /** A clock that only moves when a test says so. */
    static final class MutableClock extends Clock {

        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.now());

        void reset() {
            now.set(Instant.now());
        }

        void advance(Duration duration) {
            now.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    /** Delegates to a real store, and can be switched to throw on every call (a database outage). */
    static final class FailingGrantStore implements GrantStore {

        private final GrantStore delegate;
        private final AtomicBoolean failing = new AtomicBoolean(false);

        FailingGrantStore(GrantStore delegate) {
            this.delegate = delegate;
        }

        void failing(boolean value) {
            failing.set(value);
        }

        private void check() {
            if (failing.get()) {
                throw new IllegalStateException("The grant store is unavailable");
            }
        }

        @Override
        public void save(DelegationGrant grant) {
            check();
            delegate.save(grant);
        }

        @Override
        public Optional<DelegationGrant> findByHandleHash(String handleHash) {
            check();
            return delegate.findByHandleHash(handleHash);
        }

        @Override
        public Optional<DelegationGrant> findById(String id) {
            check();
            return delegate.findById(id);
        }

        @Override
        public void replace(DelegationGrant grant) {
            check();
            delegate.replace(grant);
        }

        @Override
        public long countActive(String employeeSubject, Instant now) {
            check();
            return delegate.countActive(employeeSubject, now);
        }
    }

    @TestConfiguration
    static class Config {

        @Bean
        @Primary
        JwtDecoder attackTestDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TOKENS.publicKey()).build();
            decoder.setJwtValidator(TokenValidators.accessToken(TestTokens.ISSUER, Set.of(TestTokens.SPA_CLIENT, TestTokens.AGENT_CLIENT)));
            return decoder;
        }

        @Bean
        @Primary
        Clock attackTestClock() {
            return CLOCK;
        }

        @Bean
        @Primary
        GrantStore attackTestStore() {
            return STORE;
        }
    }
}
