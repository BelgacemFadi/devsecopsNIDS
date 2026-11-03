package tn.esprit.studentmanagement.dtos;

/**
 * Data Transfer Object for the Department entity.
 *
 * Fixes Sonar rule S4684 - "APIs should not let clients set arbitrary
 * fields" (Mass assignment, CWE-915): the REST layer no longer binds JSON
 * directly to the JPA entity. A client can only send the 4 business fields
 * below. The generated id (idDepartment) and the managed "students"
 * collection can NEVER be pushed through a request body anymore.
 *
 * The id is kept in the record for RESPONSES (so the API still returns it);
 * the mapper simply never copies it from a request.
 */
public record DepartmentDto(
        Long idDepartment,
        String name,
        String location,
        String phone,
        String head
) {
}
