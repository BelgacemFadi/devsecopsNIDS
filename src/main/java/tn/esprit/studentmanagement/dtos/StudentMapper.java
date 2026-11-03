package tn.esprit.studentmanagement.dtos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.esprit.studentmanagement.entities.Department;
import tn.esprit.studentmanagement.entities.Student;
import tn.esprit.studentmanagement.repositories.DepartmentRepository;

/**
 * Maps between the HTTP layer (StudentDto) and the persistence layer
 * (Student entity).
 *
 * SECURITY RULE (Sonar S4684 / CWE-915):
 *   - toEntity(): the id is NEVER copied from the DTO.
 *   - updateEntity(): works on an entity loaded by @PathVariable id only.
 *   - departmentId is resolved against the database: an unknown id fails
 *     loudly instead of being silently stored.
 */
@Component
@RequiredArgsConstructor
public class StudentMapper {

    private final DepartmentRepository departmentRepository;

    /** Entity built from a request: id left null -> JPA will INSERT a new row. */
    public Student toEntity(StudentDto dto) {
        Student student = new Student();
        student.setFirstName(dto.firstName());
        student.setLastName(dto.lastName());
        student.setEmail(dto.email());
        student.setPhone(dto.phone());
        student.setDateOfBirth(dto.dateOfBirth());
        student.setAddress(dto.address());
        applyDepartment(student, dto.departmentId());
        return student;
    }

    /** Update of an existing entity loaded by id (never trusts a body id). */
    public void updateEntity(Student student, StudentDto dto) {
        student.setFirstName(dto.firstName());
        student.setLastName(dto.lastName());
        student.setEmail(dto.email());
        student.setPhone(dto.phone());
        student.setDateOfBirth(dto.dateOfBirth());
        student.setAddress(dto.address());
        applyDepartment(student, dto.departmentId());
    }

    /**
     * Attach the department reference by id. Null departmentId = keep/create
     * the student without a department.
     */
    private void applyDepartment(Student student, Long departmentId) {
        if (departmentId != null) {
            Department department = departmentRepository.findById(departmentId)
                    .orElseThrow(() -> new RuntimeException(
                            "Department not found with id: " + departmentId));
            student.setDepartment(department);
        }
    }

    public StudentDto toDto(Student student) {
        return new StudentDto(
                student.getIdStudent(),
                student.getFirstName(),
                student.getLastName(),
                student.getEmail(),
                student.getPhone(),
                student.getDateOfBirth(),
                student.getAddress(),
                student.getDepartment() != null
                        ? student.getDepartment().getIdDepartment()
                        : null
        );
    }
}
