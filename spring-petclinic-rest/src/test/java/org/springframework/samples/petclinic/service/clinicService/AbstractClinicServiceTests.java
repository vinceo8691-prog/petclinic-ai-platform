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
package org.springframework.samples.petclinic.service.clinicService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.samples.petclinic.model.*;
import org.springframework.samples.petclinic.service.OwnerService;
import org.springframework.samples.petclinic.service.PetService;
import org.springframework.samples.petclinic.service.PetTypeService;
import org.springframework.samples.petclinic.service.SpecialtyService;
import org.springframework.samples.petclinic.service.VetService;
import org.springframework.samples.petclinic.service.VisitService;
import org.springframework.samples.petclinic.util.EntityUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p> Base class for the per-aggregate service integration tests. </p> <p> Subclasses should specify Spring context
 * configuration using {@link ContextConfiguration @ContextConfiguration} annotation </p> <p>
 * AbstractclinicServiceTests and its subclasses benefit from the following services provided by the Spring
 * TestContext Framework: </p> <ul> <li><strong>Spring IoC container caching</strong> which spares us unnecessary set up
 * time between test execution.</li> <li><strong>Dependency Injection</strong> of test fixture instances, meaning that
 * we don't need to perform application context lookups. See the use of {@link Autowired @Autowired} on the service
 * instance variables, which uses autowiring <em>by
 * type</em>. <li><strong>Transaction management</strong>, meaning each test method is executed in its own transaction,
 * which is automatically rolled back by default. Thus, even if tests insert or otherwise change database state, there
 * is no need for a teardown or cleanup script. <li> An {@link org.springframework.context.ApplicationContext
 * ApplicationContext} is also inherited and can be used for explicit bean lookup if necessary. </li> </ul>
 *
 * @author Ken Krebs
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Michael Isvy
 * @author Vitaliy Fedoriv
 */
abstract class AbstractClinicServiceTests {

    @Autowired
    protected OwnerService ownerService;

    @Autowired
    protected PetService petService;

    @Autowired
    protected VisitService visitService;

    @Autowired
    protected VetService vetService;

    @Autowired
    protected PetTypeService petTypeService;

    @Autowired
    protected SpecialtyService specialtyService;

    @Test
    void shouldFindOwnersByLastName() {
        Collection<Owner> owners = this.ownerService.findOwnerByLastName("Davis");
        assertThat(owners.size()).isEqualTo(2);

        owners = this.ownerService.findOwnerByLastName("Daviss");
        assertThat(owners.isEmpty()).isTrue();
    }

    @Test
    void shouldFindSingleOwnerWithPet() {
        Owner owner = this.ownerService.findOwnerById(1);
        assertThat(owner.getLastName()).startsWith("Franklin");
        assertThat(owner.getPets().size()).isEqualTo(1);
        assertThat(owner.getPets().get(0).getType()).isNotNull();
        assertThat(owner.getPets().get(0).getType().getName()).isEqualTo("cat");
    }

    @Test
    @Transactional
    void shouldInsertOwner() {
        Collection<Owner> owners = this.ownerService.findOwnerByLastName("Schultz");
        int found = owners.size();

        Owner owner = new Owner();
        owner.setFirstName("Sam");
        owner.setLastName("Schultz");
        owner.setAddress("4, Evans Street");
        owner.setCity("Wollongong");
        owner.setTelephone("4444444444");
        this.ownerService.saveOwner(owner);
        assertThat(owner.getId().longValue()).isNotEqualTo(0);
        assertThat(owner.getPet("null value")).isNull();
        owners = this.ownerService.findOwnerByLastName("Schultz");
        assertThat(owners.size()).isEqualTo(found + 1);
    }

    @Test
    @Transactional
    void shouldUpdateOwner() {
        Owner owner = this.ownerService.findOwnerById(1);
        String oldLastName = owner.getLastName();
        String newLastName = oldLastName + "X";

        owner.setLastName(newLastName);
        this.ownerService.saveOwner(owner);

        // retrieving new name from database
        owner = this.ownerService.findOwnerById(1);
        assertThat(owner.getLastName()).isEqualTo(newLastName);
    }

    @Test
    void shouldFindPetWithCorrectId() {
        Pet pet7 = this.petService.findPetById(7);
        assertThat(pet7.getName()).startsWith("Samantha");
        assertThat(pet7.getOwner().getFirstName()).isEqualTo("Jean");

    }

//    @Test
//    void shouldFindAllPetTypes() {
//        Collection<PetType> petTypes = this.petService.findPetTypes();
//
//        PetType petType1 = EntityUtils.getById(petTypes, PetType.class, 1);
//        assertThat(petType1.getName()).isEqualTo("cat");
//        PetType petType4 = EntityUtils.getById(petTypes, PetType.class, 4);
//        assertThat(petType4.getName()).isEqualTo("snake");
//    }

    @Test
    @Transactional
    void shouldInsertPetIntoDatabaseAndGenerateId() {
        Owner owner6 = this.ownerService.findOwnerById(6);
        int found = owner6.getPets().size();

        Pet pet = new Pet();
        pet.setName("bowser");
        Collection<PetType> types = this.petService.findPetTypes();
        pet.setType(EntityUtils.getById(types, PetType.class, 2));
        pet.setBirthDate(LocalDate.now());
        owner6.addPet(pet);
        assertThat(owner6.getPets().size()).isEqualTo(found + 1);

        this.petService.savePet(pet);
        this.ownerService.saveOwner(owner6);

        owner6 = this.ownerService.findOwnerById(6);
        assertThat(owner6.getPets().size()).isEqualTo(found + 1);
        // checks that id has been generated
        assertThat(pet.getId()).isNotNull();
    }

    @Test
    @Transactional
    void shouldUpdatePetName() throws Exception {
        Pet pet7 = this.petService.findPetById(7);
        String oldName = pet7.getName();

        String newName = oldName + "X";
        pet7.setName(newName);
        this.petService.savePet(pet7);

        pet7 = this.petService.findPetById(7);
        assertThat(pet7.getName()).isEqualTo(newName);
    }

    @Test
    void shouldFindVets() {
        Collection<Vet> vets = this.vetService.findVets();

        Vet vet = EntityUtils.getById(vets, Vet.class, 3);
        assertThat(vet.getLastName()).isEqualTo("Douglas");
        assertThat(vet.getNrOfSpecialties()).isEqualTo(2);
        assertThat(vet.getSpecialties().get(0).getName()).isEqualTo("dentistry");
        assertThat(vet.getSpecialties().get(1).getName()).isEqualTo("surgery");
    }

    @Test
    @Transactional
    void shouldAddNewVisitForPet() {
        Pet pet7 = this.petService.findPetById(7);
        int found = pet7.getVisits().size();
        Visit visit = new Visit();
        pet7.addVisit(visit);
        visit.setDescription("test");
        this.visitService.saveVisit(visit);
        this.petService.savePet(pet7);

        pet7 = this.petService.findPetById(7);
        assertThat(pet7.getVisits().size()).isEqualTo(found + 1);
        assertThat(visit.getId()).isNotNull();
    }

    @Test
       void shouldFindVisitsByPetId() throws Exception {
        Collection<Visit> visits = this.visitService.findVisitsByPetId(7);
        assertThat(visits.size()).isEqualTo(2);
        Visit[] visitArr = visits.toArray(new Visit[visits.size()]);
        assertThat(visitArr[0].getPet()).isNotNull();
        assertThat(visitArr[0].getDate()).isNotNull();
        assertThat(visitArr[0].getPet().getId()).isEqualTo(7);
    }

    @Test
    void shouldFindAllPets(){
        Collection<Pet> pets = this.petService.findAllPets();
        Pet pet1 = EntityUtils.getById(pets, Pet.class, 1);
        assertThat(pet1.getName()).isEqualTo("Leo");
        Pet pet3 = EntityUtils.getById(pets, Pet.class, 3);
        assertThat(pet3.getName()).isEqualTo("Rosy");
    }

    @Test
    @Transactional
    void shouldDeletePet(){
        Pet pet = this.petService.findPetById(1);
        this.petService.deletePet(pet);
        try {
            pet = this.petService.findPetById(1);
		} catch (Exception e) {
			pet = null;
		}
        assertThat(pet).isNull();
    }

    @Test
    void shouldFindVisitDyId(){
    	Visit visit = this.visitService.findVisitById(1);
    	assertThat(visit.getId()).isEqualTo(1);
    	assertThat(visit.getPet().getName()).isEqualTo("Samantha");
    }

    @Test
    void shouldFindAllVisits(){
        Collection<Visit> visits = this.visitService.findAllVisits();
        Visit visit1 = EntityUtils.getById(visits, Visit.class, 1);
        assertThat(visit1.getPet().getName()).isEqualTo("Samantha");
        Visit visit3 = EntityUtils.getById(visits, Visit.class, 3);
        assertThat(visit3.getPet().getName()).isEqualTo("Max");
    }

    @Test
    @Transactional
    void shouldInsertVisit() {
        Collection<Visit> visits = this.visitService.findAllVisits();
        int found = visits.size();

        Pet pet = this.petService.findPetById(1);

        Visit visit = new Visit();
        visit.setPet(pet);
        visit.setDate(LocalDate.now());
        visit.setDescription("new visit");


        this.visitService.saveVisit(visit);
        assertThat(visit.getId().longValue()).isNotEqualTo(0);

        visits = this.visitService.findAllVisits();
        assertThat(visits.size()).isEqualTo(found + 1);
    }

    @Test
    @Transactional
    void shouldUpdateVisit(){
    	Visit visit = this.visitService.findVisitById(1);
    	String oldDesc = visit.getDescription();
        String newDesc = oldDesc + "X";
        visit.setDescription(newDesc);
        this.visitService.saveVisit(visit);
        visit = this.visitService.findVisitById(1);
        assertThat(visit.getDescription()).isEqualTo(newDesc);
    }

    @Test
    @Transactional
    void shouldDeleteVisit(){
    	Visit visit = this.visitService.findVisitById(1);
        this.visitService.deleteVisit(visit);
        try {
        	visit = this.visitService.findVisitById(1);
		} catch (Exception e) {
			visit = null;
		}
        assertThat(visit).isNull();
    }

    @Test
    void shouldFindVetDyId(){
    	Vet vet = this.vetService.findVetById(1);
    	assertThat(vet.getFirstName()).isEqualTo("James");
    	assertThat(vet.getLastName()).isEqualTo("Carter");
    }

    @Test
    @Transactional
    void shouldInsertVet() {
        Collection<Vet> vets = this.vetService.findAllVets();
        int found = vets.size();

        Vet vet = new Vet();
        vet.setFirstName("John");
        vet.setLastName("Dow");

        this.vetService.saveVet(vet);
        assertThat(vet.getId().longValue()).isNotEqualTo(0);

        vets = this.vetService.findAllVets();
        assertThat(vets.size()).isEqualTo(found + 1);
    }

    @Test
    @Transactional
    void shouldUpdateVet(){
    	Vet vet = this.vetService.findVetById(1);
    	String oldLastName = vet.getLastName();
        String newLastName = oldLastName + "X";
        vet.setLastName(newLastName);
        this.vetService.saveVet(vet);
        vet = this.vetService.findVetById(1);
        assertThat(vet.getLastName()).isEqualTo(newLastName);
    }

    @Test
    @Transactional
    void shouldDeleteVet(){
    	Vet vet = this.vetService.findVetById(1);
        this.vetService.deleteVet(vet);
        try {
        	vet = this.vetService.findVetById(1);
		} catch (Exception e) {
			vet = null;
		}
        assertThat(vet).isNull();
    }

    @Test
    void shouldFindAllOwners(){
        Collection<Owner> owners = this.ownerService.findAllOwners();
        Owner owner1 = EntityUtils.getById(owners, Owner.class, 1);
        assertThat(owner1.getFirstName()).isEqualTo("George");
        Owner owner3 = EntityUtils.getById(owners, Owner.class, 3);
        assertThat(owner3.getFirstName()).isEqualTo("Eduardo");
    }

    @Test
    void shouldFindOwnersPage(){
        Page<Owner> owners = this.ownerService.findOwners(null, PageRequest.of(0, 3, Sort.by("id")));
        assertThat(owners.getTotalElements()).isEqualTo(10);
        assertThat(owners.getTotalPages()).isEqualTo(4);
        assertThat(owners.getContent())
            .extracting(Owner::getFirstName)
            .containsExactly("George", "Betty", "Eduardo");
    }

    @Test
    void shouldFindOwnersPageByLastName(){
        Page<Owner> owners = this.ownerService.findOwners("Davis", PageRequest.of(0, 1, Sort.by("id")));
        assertThat(owners.getTotalElements()).isEqualTo(2);
        assertThat(owners.getTotalPages()).isEqualTo(2);
        assertThat(owners.getContent())
            .extracting(Owner::getFirstName)
            .containsExactly("Betty");
    }

    private static Sort ownersByLastName(Sort.Direction direction) {
        return Sort.by(
            new Sort.Order(direction, "lastName").ignoreCase(),
            new Sort.Order(direction, "firstName").ignoreCase(),
            new Sort.Order(direction, "id"));
    }

    private void saveOwner(String firstName, String lastName) {
        Owner owner = new Owner();
        owner.setFirstName(firstName);
        owner.setLastName(lastName);
        owner.setAddress("1 Test St.");
        owner.setCity("Testville");
        owner.setTelephone("5555555555");
        this.ownerService.saveOwner(owner);
    }

    @Test
    void shouldFindOwnersPageSortedByLastNameAscending() {
        Page<Owner> owners = this.ownerService.findOwners(null, PageRequest.of(0, 10, ownersByLastName(Sort.Direction.ASC)));
        assertThat(owners.getContent())
            .extracting(Owner::getLastName)
            .containsExactly("Black", "Coleman", "Davis", "Davis", "Escobito", "Estaban", "Franklin", "McTavish", "Rodriquez", "Schroeder");
    }

    @Test
    void shouldFindOwnersPageSortedByLastNameDescendingAsTheExactReverse() {
        Page<Owner> ascending = this.ownerService.findOwners(null, PageRequest.of(0, 10, ownersByLastName(Sort.Direction.ASC)));
        Page<Owner> descending = this.ownerService.findOwners(null, PageRequest.of(0, 10, ownersByLastName(Sort.Direction.DESC)));
        assertThat(descending.getContent())
            .extracting(Owner::getId)
            .containsExactlyElementsOf(ascending.getContent().stream().map(Owner::getId).toList().reversed());
    }

    @Test
    @Transactional
    void shouldSortOwnersByLastNameIgnoringCase() {
        saveOwner("Abe", "aardvark");
        Page<Owner> owners = this.ownerService.findOwners(null, PageRequest.of(0, 3, ownersByLastName(Sort.Direction.ASC)));
        assertThat(owners.getContent())
            .extracting(Owner::getLastName)
            .containsExactly("aardvark", "Black", "Coleman");
    }

    @Test
    @Transactional
    void shouldBreakLastNameTiesOnFirstNameThenId() {
        saveOwner("Zoe", "Tiebreak");
        saveOwner("Abe", "Tiebreak");
        saveOwner("Abe", "Tiebreak");
        Page<Owner> owners = this.ownerService.findOwners("Tiebreak", PageRequest.of(0, 10, ownersByLastName(Sort.Direction.ASC)));
        assertThat(owners.getContent()).extracting(Owner::getFirstName).containsExactly("Abe", "Abe", "Zoe");
        assertThat(owners.getContent().get(0).getId()).isLessThan(owners.getContent().get(1).getId());
    }

    @Test
    void shouldPageThroughLastNameSortWithoutRepeatingOrSkippingOwners() {
        Sort sort = ownersByLastName(Sort.Direction.ASC);
        List<String> seen = new java.util.ArrayList<>();
        for (int pageNumber = 0; pageNumber < 4; pageNumber++) {
            this.ownerService.findOwners(null, PageRequest.of(pageNumber, 3, sort))
                .forEach(owner -> seen.add(owner.getFirstName() + " " + owner.getLastName()));
        }
        assertThat(seen).hasSize(10).doesNotHaveDuplicates();
    }

    @Test
    void shouldFindOwnersPageByLastNameIgnoringCase() {
        for (String search : List.of("davis", "DAVIS", "dAv", "Davis")) {
            Page<Owner> owners = this.ownerService.findOwners(search, PageRequest.of(0, 10, Sort.by("id")));
            assertThat(owners.getTotalElements()).as("search for %s", search).isEqualTo(2);
            assertThat(owners.getContent()).extracting(Owner::getLastName).containsOnly("Davis");
        }
    }

    @Test
    void shouldFindOwnersByLastNameIgnoringCase() {
        assertThat(this.ownerService.findOwnerByLastName("davis")).hasSize(2);
        assertThat(this.ownerService.findOwnerByLastName("BLACK")).extracting(Owner::getFirstName).containsExactly("Jeff");
        assertThat(this.ownerService.findOwnerByLastName("zzz")).isEmpty();
    }

    @Test
    @Transactional
    void shouldMatchPercentUnderscoreAndBangLiterallyInThePagedSearch() {
        saveOwner("A", "Per%cent");
        saveOwner("B", "Perxcent");
        saveOwner("C", "Under_score");
        saveOwner("D", "Underxscore");
        saveOwner("E", "Bang!");
        PageRequest firstPage = PageRequest.of(0, 10, Sort.by("id"));

        assertThat(this.ownerService.findOwners("Per%", firstPage).getContent()).extracting(Owner::getLastName).containsExactly("Per%cent");
        assertThat(this.ownerService.findOwners("Per", firstPage).getContent()).extracting(Owner::getLastName).containsExactly("Per%cent", "Perxcent");
        assertThat(this.ownerService.findOwners("Under_", firstPage).getContent()).extracting(Owner::getLastName).containsExactly("Under_score");
        assertThat(this.ownerService.findOwners("Bang!", firstPage).getContent()).extracting(Owner::getLastName).containsExactly("Bang!");
        // A bare wildcard or escape character is just text, so it matches nothing here.
        assertThat(this.ownerService.findOwners("%", firstPage).getTotalElements()).isZero();
        assertThat(this.ownerService.findOwners("_", firstPage).getTotalElements()).isZero();
        assertThat(this.ownerService.findOwners("!", firstPage).getTotalElements()).isZero();
        assertThat(this.ownerService.findOwners("D_vis", firstPage).getTotalElements()).isZero();
    }

    @Test
    @Transactional
    void shouldMatchPercentUnderscoreAndBangLiterallyInTheUnpagedSearch() {
        saveOwner("A", "Per%cent");
        saveOwner("B", "Perxcent");
        saveOwner("C", "Under_score");
        saveOwner("D", "Underxscore");

        assertThat(this.ownerService.findOwnerByLastName("Per%")).extracting(Owner::getLastName).containsExactly("Per%cent");
        assertThat(this.ownerService.findOwnerByLastName("Under_")).extracting(Owner::getLastName).containsExactly("Under_score");
        assertThat(this.ownerService.findOwnerByLastName("%")).isEmpty();
        assertThat(this.ownerService.findOwnerByLastName("D_vis")).isEmpty();
    }

    @Test
    @Transactional
    void shouldDeleteOwner(){
    	Owner owner = this.ownerService.findOwnerById(1);
        this.ownerService.deleteOwner(owner);
        try {
        	owner = this.ownerService.findOwnerById(1);
		} catch (Exception e) {
			owner = null;
		}
        assertThat(owner).isNull();
    }

    @Test
    void shouldFindPetTypeById(){
    	PetType petType = this.petTypeService.findPetTypeById(1);
    	assertThat(petType.getName()).isEqualTo("cat");
    }

    @Test
    void shouldFindAllPetTypes(){
        Collection<PetType> petTypes = this.petTypeService.findAllPetTypes();
        PetType petType1 = EntityUtils.getById(petTypes, PetType.class, 1);
        assertThat(petType1.getName()).isEqualTo("cat");
        PetType petType3 = EntityUtils.getById(petTypes, PetType.class, 3);
        assertThat(petType3.getName()).isEqualTo("lizard");
    }

    @Test
    @Transactional
    void shouldInsertPetType() {
        Collection<PetType> petTypes = this.petTypeService.findAllPetTypes();
        int found = petTypes.size();

        PetType petType = new PetType();
        petType.setName("tiger");

        this.petTypeService.savePetType(petType);
        assertThat(petType.getId().longValue()).isNotEqualTo(0);

        petTypes = this.petTypeService.findAllPetTypes();
        assertThat(petTypes.size()).isEqualTo(found + 1);
    }

    @Test
    @Transactional
    void shouldUpdatePetType(){
    	PetType petType = this.petTypeService.findPetTypeById(1);
    	String oldLastName = petType.getName();
        String newLastName = oldLastName + "X";
        petType.setName(newLastName);
        this.petTypeService.savePetType(petType);
        petType = this.petTypeService.findPetTypeById(1);
        assertThat(petType.getName()).isEqualTo(newLastName);
    }

    @Test
    @Transactional
    void shouldDeletePetType(){
    	PetType petType = this.petTypeService.findPetTypeById(1);
        this.petTypeService.deletePetType(petType);
        clearCache();
        try {
        	petType = this.petTypeService.findPetTypeById(1);
		} catch (Exception e) {
			petType = null;
		}
        assertThat(petType).isNull();
    }

    @Test
    void shouldFindSpecialtyById(){
    	Specialty specialty = this.specialtyService.findSpecialtyById(1);
    	assertThat(specialty.getName()).isEqualTo("radiology");
    }

    @Test
    void shouldFindAllSpecialtys(){
        Collection<Specialty> specialties = this.specialtyService.findAllSpecialties();
        Specialty specialty1 = EntityUtils.getById(specialties, Specialty.class, 1);
        assertThat(specialty1.getName()).isEqualTo("radiology");
        Specialty specialty3 = EntityUtils.getById(specialties, Specialty.class, 3);
        assertThat(specialty3.getName()).isEqualTo("dentistry");
    }

    @Test
    @Transactional
    void shouldInsertSpecialty() {
        Collection<Specialty> specialties = this.specialtyService.findAllSpecialties();
        int found = specialties.size();

        Specialty specialty = new Specialty();
        specialty.setName("dermatologist");

        this.specialtyService.saveSpecialty(specialty);
        assertThat(specialty.getId().longValue()).isNotEqualTo(0);

        specialties = this.specialtyService.findAllSpecialties();
        assertThat(specialties.size()).isEqualTo(found + 1);
    }

    @Test
    @Transactional
    void shouldUpdateSpecialty(){
    	Specialty specialty = this.specialtyService.findSpecialtyById(1);
    	String oldLastName = specialty.getName();
        String newLastName = oldLastName + "X";
        specialty.setName(newLastName);
        this.specialtyService.saveSpecialty(specialty);
        specialty = this.specialtyService.findSpecialtyById(1);
        assertThat(specialty.getName()).isEqualTo(newLastName);
    }

    @Test
    @Transactional
    void shouldDeleteSpecialty(){
        Specialty specialty = new Specialty();
        specialty.setName("test");
        this.specialtyService.saveSpecialty(specialty);
        Integer specialtyId = specialty.getId();
        assertThat(specialtyId).isNotNull();
    	specialty = this.specialtyService.findSpecialtyById(specialtyId);
        assertThat(specialty).isNotNull();
        this.specialtyService.deleteSpecialty(specialty);
        try {
        	specialty = this.specialtyService.findSpecialtyById(specialtyId);
		} catch (Exception e) {
			specialty = null;
		}
        assertThat(specialty).isNull();
    }

    @Test
    @Transactional
    void shouldFindSpecialtiesByNameIn() {
        Specialty specialty1 = new Specialty();
        specialty1.setName("radiology");
        specialty1.setId(1);
        Specialty specialty2 = new Specialty();
        specialty2.setName("surgery");
        specialty2.setId(2);
        Specialty specialty3 = new Specialty();
        specialty3.setName("dentistry");
        specialty3.setId(3);
        List<Specialty> expectedSpecialties = List.of(specialty1, specialty2, specialty3);
        Set<String> specialtyNames = expectedSpecialties.stream()
            .map(Specialty::getName)
            .collect(Collectors.toSet());
        Collection<Specialty> actualSpecialties = this.specialtyService.findSpecialtiesByNameIn(specialtyNames);
        assertThat(actualSpecialties).isNotNull();
        assertThat(actualSpecialties.size()).isEqualTo(expectedSpecialties.size());
        for (Specialty expected : expectedSpecialties) {
            assertThat(actualSpecialties.stream()
                .anyMatch(
                    actual -> actual.getName().equals(expected.getName())
                    && actual.getId().equals(expected.getId()))).isTrue();
        }
    }

    void clearCache() {}
}
