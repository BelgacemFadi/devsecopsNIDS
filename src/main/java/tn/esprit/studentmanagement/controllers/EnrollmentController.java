package tn.esprit.studentmanagement.controllers;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tn.esprit.studentmanagement.dtos.EnrollmentDto;
import tn.esprit.studentmanagement.dtos.EnrollmentMapper;
import tn.esprit.studentmanagement.entities.Enrollment;
import tn.esprit.studentmanagement.services.IEnrollment;

import java.util.List;

/**
 * Fixed for Sonar S4684 (Mass assignment, CWE-915):
 *   - request bodies are EnrollmentDto records, not the JPA entity, so a
 *     client can no longer set idEnrollment or push whole Student / Course
 *     objects through the body (only flat studentId / courseId references);
 *   - PUT takes the id from the PATH (/updateEnrollment/{id}), never from the body;
 *   - responses are DTOs too (no leaked entity graph, no infinite JSON recursion).
 */
@RestController
@RequestMapping("/Enrollment")
@CrossOrigin(origins = "http://localhost:4200")
@AllArgsConstructor
public class EnrollmentController {

    private final IEnrollment enrollmentService;
    private final EnrollmentMapper enrollmentMapper;

    @GetMapping("/getAllEnrollment")
    public List<EnrollmentDto> getAllEnrollment() {
        return enrollmentService.getAllEnrollments()
                .stream()
                .map(enrollmentMapper::toDto)
                .toList();
    }

    @GetMapping("/getEnrollment/{id}")
    public EnrollmentDto getEnrollment(@PathVariable Long id) {
        return enrollmentMapper.toDto(enrollmentService.getEnrollmentById(id));
    }

    @PostMapping("/createEnrollment")
    public EnrollmentDto createEnrollment(@RequestBody EnrollmentDto dto) {
        Enrollment saved = enrollmentService.saveEnrollment(enrollmentMapper.toEntity(dto));
        return enrollmentMapper.toDto(saved);
    }

    @PutMapping("/updateEnrollment/{id}")
    public EnrollmentDto updateEnrollment(@PathVariable Long id, @RequestBody EnrollmentDto dto) {
        Enrollment existing = enrollmentService.getEnrollmentById(id);
        enrollmentMapper.updateEntity(existing, dto);
        return enrollmentMapper.toDto(enrollmentService.saveEnrollment(existing));
    }

    @DeleteMapping("/deleteEnrollment/{id}")
    public void deleteEnrollment(@PathVariable Long id) {
        enrollmentService.deleteEnrollment(id);
    }
}
