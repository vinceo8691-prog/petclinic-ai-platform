package org.springframework.samples.petclinic.model;

/**
 * A pet type with how many pets are of that type and how many visits those pets have had. Read-only: it is what
 * the delete confirmation for a pet type needs, because deleting a pet type deletes its pets and their visits.
 *
 * @param id         the pet type id
 * @param name       the pet type name
 * @param petCount   the number of pets of this type
 * @param visitCount the number of visits made by the pets of this type
 */
public record PetTypeWithCounts(Integer id, String name, long petCount, long visitCount) {
}
