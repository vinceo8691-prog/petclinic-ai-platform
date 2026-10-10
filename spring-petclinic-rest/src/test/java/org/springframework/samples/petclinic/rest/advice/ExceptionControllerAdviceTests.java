package org.springframework.samples.petclinic.rest.advice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.rest.dto.ValidationMessageDto;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the 400 mappings in {@link ExceptionControllerAdvice}. The end-to-end behavior for request
 * parameters and request bodies is covered in {@code OwnerRestControllerV2IntegrationTests}; this class covers the
 * path a request body cannot reach, an entity rule violated when the entity is saved.
 */
class ExceptionControllerAdviceTests {

    private final ExceptionControllerAdvice advice = new ExceptionControllerAdvice();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/owners");

    @SuppressWarnings("unchecked")
    private static List<ValidationMessageDto> errorsOf(ResponseEntity<ProblemDetail> response) {
        return (List<ValidationMessageDto>) response.getBody().getProperties().get("schemaValidationErrors");
    }

    private static Owner ownerWithTelephone(String telephone) {
        Owner owner = new Owner();
        owner.setFirstName("Sam");
        owner.setLastName("Smith");
        owner.setAddress("1 Main St.");
        owner.setCity("Madison");
        owner.setTelephone(telephone);
        return owner;
    }

    @Test
    void anEntityRuleViolatedOnSaveIsABadRequestNotAServerError() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<Owner>> violations = validator.validate(ownerWithTelephone("12345"));

        ResponseEntity<ProblemDetail> response = advice.handleConstraintViolationException(
            new ConstraintViolationException(violations), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getTitle()).isEqualTo("ConstraintViolationException");
        assertThat(errorsOf(response)).isNotEmpty();
        assertThat(errorsOf(response).get(0).getAdditionalProperties())
            .containsEntry("field", "telephone")
            .containsEntry("rejectedValue", "12345")
            .containsEntry("defaultMessage", "Phone number must be exactly 10 digits");
    }

    @Test
    void aValidOwnerHasNoViolations() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(validator.validate(ownerWithTelephone("6085551234"))).isEmpty();
    }

    @Test
    void aTypeMismatchNamesTheParameterAndTheExpectedType() throws NoSuchMethodException {
        MethodParameter parameter = new MethodParameter(Owner.class.getMethod("setTelephone", String.class), 0);
        MethodArgumentTypeMismatchException mismatch =
            new MethodArgumentTypeMismatchException("abc", Integer.class, "page", parameter, new NumberFormatException());

        ResponseEntity<ProblemDetail> response = advice.handleMethodArgumentTypeMismatchException(mismatch, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorsOf(response)).hasSize(1);
        assertThat(errorsOf(response).get(0).getAdditionalProperties())
            .containsEntry("field", "page")
            .containsEntry("rejectedValue", "abc")
            .containsEntry("defaultMessage", "must be a valid Integer");
    }

    @Test
    void theGeneralHandlerStillAnswers500ForAnUnexpectedError() {
        ResponseEntity<ProblemDetail> response = advice.handleGeneralException(new IllegalStateException("boom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getDetail()).doesNotContain("boom");
    }
}
