package tn.esprit.studentmanagement.dtos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.esprit.studentmanagement.entities.Course;
import tn.esprit.studentmanagement.entities.Enrollment;
import tn.esprit.studentmanagement.entities.Student;
import tn.esprit.studentmanagement.repositories.CourseRepository;
import tn.esprit.studentmanagement.repositories.StudentRepository;

/**
 * Maps between the HTTP layer (EnrollmentDto) and the persistence layer
 * (Enrollment entity).
 *
 * SECURITY RULE (Sonar S4684 / CWE-915):
 *   - toEntity(): the id is NEVER copied from the DTO.
 *   - updateEntity(): works on an entity loaded by @PathVariable id only.
 *   - studentId / courseId are resolved against the database: unknown ids
 *     fail loudly instead of being silently stored.
 */
@Component
@RequiredArgsConstructor
public class EnrollmentMapper {

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;

    /** Entity built from a request: id left null -> JPA will INSERT a new row. */
    public Enrollment toEntity(EnrollmentDto dto) {
        Enrollment enrollment = new Enrollment();
        enrollment.setEnrollmentDate(dto.enrollmentDate());
        enrollment.setGrade(dto.grade());
        enrollment.setStatus(dto.status());
        applyRelations(enrollment, dto.studentId(), dto.courseId());
        return enrollment;
    }

    /** Update of an existing entity loaded by id (never trusts a body id). */
    public void updateEntity(Enrollment enrollment, EnrollmentDto dto) {
        enrollment.setEnrollmentDate(dto.enrollmentDate());
        enrollment.setGrade(dto.grade());
        enrollment.setStatus(dto.status());
        applyRelations(enrollment, dto.studentId(), dto.courseId());
    }

    private void applyRelations(Enrollment enrollment, Long studentId, Long courseId) {
        if (studentId != null) {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException(
                            "Student not found with id: " + studentId));
            enrollment.setStudent(student);
        }
        if (courseId != null) {
            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new RuntimeException(
                            "Course not found with id: " + courseId));
            enrollment.setCourse(course);
        }
    }

    public EnrollmentDto toDto(Enrollment enrollment) {
        return new EnrollmentDto(
                enrollment.getIdEnrollment(),
                enrollment.getEnrollmentDate(),
                enrollment.getGrade(),
                enrollment.getStatus(),
                enrollment.getStudent() != null
                        ? enrollment.getStudent().getIdStudent()
                        : null,
                enrollment.getCourse() != null
                        ? enrollment.getCourse().getIdCourse()
                        : null
        );
    }
}
