package org.springframework.samples.petclinic.rest.controller.v2;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.mapper.OwnerMapper;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.rest.api.OwnerV2Api;
import org.springframework.samples.petclinic.rest.dto.OwnerPageDto;
import org.springframework.samples.petclinic.service.OwnerService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("/api")
public class OwnerRestControllerV2 implements OwnerV2Api {

    private final OwnerService ownerService;
    private final OwnerMapper ownerMapper;

    public OwnerRestControllerV2(OwnerService ownerService, OwnerMapper ownerMapper) {
        this.ownerService = ownerService;
        this.ownerMapper = ownerMapper;
    }

    @Override
    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    public ResponseEntity<OwnerPageDto> listOwnersPage(String lastName, String sort, String direction, Integer page, Integer size) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? 20 : size;
        Page<Owner> owners = this.ownerService.findOwners(
            lastName,
            PageRequest.of(pageNumber, pageSize, toSort(sort, direction)));
        return new ResponseEntity<>(ownerMapper.toOwnerPageDto(owners), HttpStatus.OK);
    }

    /**
     * Builds the page sort from the request. Only the two whitelisted fields can reach the query: a
     * client-supplied string is never used as a property name. {@code sort=lastName} orders by last name,
     * then first name (both ignoring case), then id, so paging stays stable when many owners share a last
     * name; the direction applies to every key, so descending is the exact reverse of ascending. Anything
     * other than {@code lastName} sorts by id, which is also the default.
     */
    private static Sort toSort(String sort, String direction) {
        Sort.Direction sortDirection = "desc".equals(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        if ("lastName".equals(sort)) {
            return Sort.by(
                new Sort.Order(sortDirection, "lastName").ignoreCase(),
                new Sort.Order(sortDirection, "firstName").ignoreCase(),
                new Sort.Order(sortDirection, "id"));
        }
        return Sort.by(sortDirection, "id");
    }
}
