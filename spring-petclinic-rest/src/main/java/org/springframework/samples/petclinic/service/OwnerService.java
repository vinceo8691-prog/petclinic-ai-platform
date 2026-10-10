package org.springframework.samples.petclinic.service;

import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.repository.OwnerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

@Service
public class OwnerService {

    private final OwnerRepository ownerRepository;

    public OwnerService(OwnerRepository ownerRepository) {
        this.ownerRepository = ownerRepository;
    }

    @Transactional(readOnly = true)
    public Collection<Owner> findAllOwners() throws DataAccessException {
        return ownerRepository.findAll();
    }

    /**
     * Case-insensitive prefix search by last name, or every owner when {@code lastName} is null. The text is
     * matched literally: a user-typed {@code %} or {@code _} is not a wildcard.
     */
    @Transactional(readOnly = true)
    public Page<Owner> findOwners(String lastName, Pageable pageable) throws DataAccessException {
        if (lastName != null) {
            return ownerRepository.findByLastName(escapeLikePattern(lastName), pageable);
        }
        return ownerRepository.findAll(pageable);
    }

    /**
     * Escapes the LIKE wildcards and the escape character itself so user input is matched literally. The
     * repository queries declare {@code ESCAPE '!'}; {@code !} is used instead of a backslash because
     * databases such as MySQL treat a backslash specially inside string literals.
     */
    static String escapeLikePattern(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    @Transactional
    public void deleteOwner(Owner owner) throws DataAccessException {
        ownerRepository.delete(owner);
    }

    @Transactional(readOnly = true)
    public Owner findOwnerById(int id) throws DataAccessException {
        return EntityLookup.findOrNull(() -> ownerRepository.findById(id));
    }

    @Transactional
    public void saveOwner(Owner owner) throws DataAccessException {
        ownerRepository.save(owner);
    }

    @Transactional(readOnly = true)
    public Collection<Owner> findOwnerByLastName(String lastName) throws DataAccessException {
        return ownerRepository.findByLastName(escapeLikePattern(lastName));
    }

}
