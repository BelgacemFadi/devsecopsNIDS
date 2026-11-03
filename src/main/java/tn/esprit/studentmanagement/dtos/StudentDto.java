package tn.esprit.studentmanagement.dtos;

import java.time.LocalDate;

/**
 * Data Transfer Object for the Student entity.
 *
 * - Flat "departmentId" instead of the full Department object: the client
 *   references an existing department BY ID, it cannot rewrite a whole
 *   Department object (nor its id) through the Student endpoint.
 * - No "enrollments" field: enrollments must be managed by the
 *   EnrollmentController, not pushed silently through a Student create/update.
 * - Flat id also breaks the Student -> Department -> Student infinite
 *   JSON recursion the raw entity serialization would cause.
 */
public record StudentDto(
        Long idStudent,
        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        String address,
        Long departmentId
) {
}
