package tn.esprit.studentmanagement.dtos;

import org.springframework.stereotype.Component;
import tn.esprit.studentmanagement.entities.Department;

/**
 * Maps between the HTTP layer (DepartmentDto) and the persistence layer
 * (Department entity).
 *
 * SECURITY RULE (Sonar S4684 / CWE-915):
 *   - toEntity(): the id is NEVER copied from the DTO, so a client cannot
 *     create or overwrite the row of its choice by sending an id in the body.
 *   - updateEntity(): works on an entity loaded by @PathVariable id only.
 *   - toDto(): the "students" collection is not exposed (no data leak,
 *     no infinite Student -> Department -> Student JSON recursion).
 */
@Component
public class DepartmentMapper {

    /** Entity built from a request: id left null -> JPA will INSERT a new row. */
    public Department toEntity(DepartmentDto dto) {
        Department department = new Department();
        department.setName(dto.name());
        department.setLocation(dto.location());
        department.setPhone(dto.phone());
        department.setHead(dto.head());
        return department;
    }

    /** Update of an existing entity loaded by id (never trusts a body id). */
    public void updateEntity(Department department, DepartmentDto dto) {
        department.setName(dto.name());
        department.setLocation(dto.location());
        department.setPhone(dto.phone());
        department.setHead(dto.head());
    }

    public DepartmentDto toDto(Department department) {
        return new DepartmentDto(
                department.getIdDepartment(),
                department.getName(),
                department.getLocation(),
                department.getPhone(),
                department.getHead()
        );
    }
}
