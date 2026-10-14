package org.springframework.samples.petclinic.service;

import org.springframework.dao.DataAccessException;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.samples.petclinic.model.PetTypeWithCounts;
import org.springframework.samples.petclinic.repository.PetTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
public class PetTypeService {

    private final PetTypeRepository petTypeRepository;

    public PetTypeService(PetTypeRepository petTypeRepository) {
        this.petTypeRepository = petTypeRepository;
    }

    @Transactional(readOnly = true)
    public PetType findPetTypeById(int petTypeId) {
        return EntityLookup.findOrNull(() -> petTypeRepository.findById(petTypeId));
    }

    @Transactional(readOnly = true)
    public Collection<PetType> findAllPetTypes() throws DataAccessException {
        return petTypeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<PetTypeWithCounts> findAllPetTypesWithCounts() throws DataAccessException {
        return petTypeRepository.findAllWithCounts();
    }

    @Transactional(readOnly = true)
    public PetTypeWithCounts findPetTypeWithCountsById(int petTypeId) throws DataAccessException {
        return petTypeRepository.findWithCountsById(petTypeId);
    }

    @Transactional
    public void savePetType(PetType petType) throws DataAccessException {
        petTypeRepository.save(petType);
    }

    @Transactional
    public void deletePetType(PetType petType) throws DataAccessException {
        petTypeRepository.delete(petType);
    }

}
