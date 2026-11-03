package tn.esprit.studentmanagement.dtos;

import tn.esprit.studentmanagement.entities.Status;

import java.time.LocalDate;

/**
 * Data Transfer Object for the Enrollment entity.
 *
 * "studentId" / "courseId" are flat references instead of full entity
 * objects: the client references existing rows BY ID, it cannot push whole
 * Student / Course objects (and their ids) through this endpoint.
 */
public record EnrollmentDto(
        Long idEnrollment,
        LocalDate enrollmentDate,
        Double grade,
        Status status,
        Long studentId,
        Long courseId
) {
}
