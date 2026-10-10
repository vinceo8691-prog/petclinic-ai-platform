package org.springframework.samples.petclinic.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit tests for {@link OwnerService#escapeLikePattern(String)}: the escape character is {@code !} and it
 * must be escaped first so the characters added for {@code %} and {@code _} are not escaped twice.
 */
class OwnerServiceEscapeTests {

    @Test
    void leavesOrdinaryTextUnchanged() {
        assertThat(OwnerService.escapeLikePattern("Davis")).isEqualTo("Davis");
        assertThat(OwnerService.escapeLikePattern("")).isEmpty();
    }

    @Test
    void escapesPercentAndUnderscore() {
        assertThat(OwnerService.escapeLikePattern("100%")).isEqualTo("100!%");
        assertThat(OwnerService.escapeLikePattern("a_b")).isEqualTo("a!_b");
    }

    @Test
    void escapesTheEscapeCharacterItself() {
        assertThat(OwnerService.escapeLikePattern("Bang!")).isEqualTo("Bang!!");
    }

    @Test
    void escapesTheEscapeCharacterBeforeTheWildcards() {
        assertThat(OwnerService.escapeLikePattern("!%_")).isEqualTo("!!!%!_");
    }

    @Test
    void doesNotTreatBackslashSpecially() {
        assertThat(OwnerService.escapeLikePattern("a\\b")).isEqualTo("a\\b");
    }
}
