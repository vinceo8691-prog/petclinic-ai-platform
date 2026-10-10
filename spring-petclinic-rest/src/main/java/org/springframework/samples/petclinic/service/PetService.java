package org.springframework.samples.petclinic.service;

import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.samples.petclinic.repository.PetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

@Service
public class PetService {

    private final PetRepository petRepository;
    private final PetTypeService petTypeService;

    public PetService(PetRepository petRepository, PetTypeService petTypeService) {
        this.petRepository = petRepository;
        this.petTypeService = petTypeService;
    }

    @Transactional(readOnly = true)
    public Collection<Pet> findAllPets() throws DataAccessException {
        return petRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<Pet> findPets(Pageable pageable) throws DataAccessException {
        return petRepository.findAll(pageable);
    }

    @Transactional
    public void deletePet(Pet pet) throws DataAccessException {
        petRepository.delete(pet);
    }

    @Transactional(readOnly = true)
    public Collection<PetType> findPetTypes() throws DataAccessException {
        return petRepository.findPetTypes();
    }

    @Transactional(readOnly = true)
    public Pet findPetById(int id) throws DataAccessException {
        return EntityLookup.findOrNull(() -> petRepository.findById(id));
    }

    @Transactional
    public void savePet(Pet pet) throws DataAccessException {
        pet.setType(petTypeService.findPetTypeById(pet.getType().getId()));
        petRepository.save(pet);
    }

    // Takes the already-loaded Owner rather than an id so PetService doesn't need
    // a dependency on OwnerService (see docs/architecture/pet-owner-relationship-wiring.md) -- owner.addPet wires both sides
    // of the relationship, which hand-setting pet.setOwner(owner) in the caller used to skip.
    @Transactional
    public void addPetToOwner(Owner owner, Pet pet) throws DataAccessException {
        owner.addPet(pet);
        savePet(pet);
    }

}
