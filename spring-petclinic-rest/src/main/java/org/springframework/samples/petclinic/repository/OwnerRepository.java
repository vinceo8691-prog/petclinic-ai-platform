/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.repository;

import java.util.Collection;

import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.model.Owner;

/**
 * Spring Data JPA repository for <code>Owner</code> domain objects.
 *
 * @author Ken Krebs
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Michael Isvy
 * @author Vitaliy Fedoriv
 */
public interface OwnerRepository extends Repository<Owner, Integer> {

    /**
     * Retrieve <code>Owner</code>s from the data store by last name, returning all owners whose last name <i>starts</i>
     * with the given name, ignoring case. The value is used as a LIKE pattern prefix with <code>!</code> as the
     * escape character, so callers must escape <code>!</code>, <code>%</code> and <code>_</code> in user input
     * (see <code>OwnerService</code>).
     *
     * @param lastName Value to search for, already escaped
     * @return a <code>Collection</code> of matching <code>Owner</code>s (or an empty <code>Collection</code> if none
     * found)
     */
    @Query("SELECT DISTINCT owner FROM Owner owner left join fetch owner.pets "
        + "WHERE LOWER(owner.lastName) LIKE LOWER(CONCAT(:lastName, '%')) ESCAPE '!'")
    Collection<Owner> findByLastName(@Param("lastName") String lastName);

    /**
     * Case-insensitive prefix search by last name, paged. As in the unpaged variant, <code>lastName</code> is a
     * LIKE pattern prefix that uses <code>!</code> as its escape character, so callers must escape <code>!</code>,
     * <code>%</code> and <code>_</code> in user input (see <code>OwnerService</code>).
     */
    @Query(
        value = "SELECT owner FROM Owner owner WHERE LOWER(owner.lastName) LIKE LOWER(CONCAT(:lastName, '%')) ESCAPE '!'",
        countQuery = "SELECT COUNT(owner) FROM Owner owner WHERE LOWER(owner.lastName) LIKE LOWER(CONCAT(:lastName, '%')) ESCAPE '!'")
    Page<Owner> findByLastName(@Param("lastName") String lastName, Pageable pageable);

    /**
     * Retrieve an <code>Owner</code> from the data store by id.
     *
     * @param id the id to search for
     * @return the <code>Owner</code> if found
     * @throws org.springframework.dao.DataRetrievalFailureException if not found
     */
    @Query("SELECT owner FROM Owner owner left join fetch owner.pets WHERE owner.id =:id")
    Owner findById(@Param("id") int id);

    /**
     * Save an <code>Owner</code> to the data store, either inserting or updating it.
     *
     * @param owner the <code>Owner</code> to save
     * @see BaseEntity#isNew
     */
    void save(Owner owner) throws DataAccessException;

    /**
     * Retrieve <code>Owner</code>s from the data store, returning all owners
     *
     * @return a <code>Collection</code> of <code>Owner</code>s (or an empty <code>Collection</code> if none
     * found)
     */
    Collection<Owner> findAll() throws DataAccessException;

    @Query(
        value = "SELECT owner FROM Owner owner",
        countQuery = "SELECT COUNT(owner) FROM Owner owner")
    Page<Owner> findAll(Pageable pageable);

    /**
     * Delete an <code>Owner</code> to the data store by <code>Owner</code>.
     *
     * @param owner the <code>Owner</code> to delete
     *
     */
    void delete(Owner owner) throws DataAccessException;

}
