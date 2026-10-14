/*
 * Copyright 2016-2017 the original author or authors.
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
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.samples.petclinic.model.PetTypeWithCounts;

/**
 * Spring Data JPA repository for <code>PetType</code> domain objects.
 *
 * @author Vitaliy Fedoriv
 *
 */
public interface PetTypeRepository extends Repository<PetType, Integer>, PetTypeRepositoryOverride {

	PetType findById(int id) throws DataAccessException;

    PetType findByName(String name) throws DataAccessException;

	Collection<PetType> findAll() throws DataAccessException;

	void save(PetType petType) throws DataAccessException;

	void delete(PetType petType) throws DataAccessException;

	/**
	 * Every pet type with its pet count and visit count, in id order. Pets are counted distinct because the join to
	 * visits repeats a pet once per visit; a type with no pets (or a pet with no visits) counts 0.
	 */
	@Query("""
		SELECT new org.springframework.samples.petclinic.model.PetTypeWithCounts(
			petType.id, petType.name, COUNT(DISTINCT pet.id), COUNT(visit.id))
		FROM PetType petType
		LEFT JOIN Pet pet ON pet.type = petType
		LEFT JOIN pet.visits visit
		GROUP BY petType.id, petType.name
		ORDER BY petType.id
		""")
	List<PetTypeWithCounts> findAllWithCounts() throws DataAccessException;

	/** The same counts for one pet type, or {@code null} when there is no such type. */
	@Query("""
		SELECT new org.springframework.samples.petclinic.model.PetTypeWithCounts(
			petType.id, petType.name, COUNT(DISTINCT pet.id), COUNT(visit.id))
		FROM PetType petType
		LEFT JOIN Pet pet ON pet.type = petType
		LEFT JOIN pet.visits visit
		WHERE petType.id = :petTypeId
		GROUP BY petType.id, petType.name
		""")
	PetTypeWithCounts findWithCountsById(@Param("petTypeId") int petTypeId) throws DataAccessException;

}
