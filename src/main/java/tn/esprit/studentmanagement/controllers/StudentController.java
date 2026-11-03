package tn.esprit.studentmanagement.controllers;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tn.esprit.studentmanagement.dtos.StudentDto;
import tn.esprit.studentmanagement.dtos.StudentMapper;
import tn.esprit.studentmanagement.entities.Student;
import tn.esprit.studentmanagement.services.IStudentService;

import java.util.List;

/**
 * Fixed for Sonar S4684 (Mass assignment, CWE-915):
 *   - request bodies are StudentDto records, not the JPA entity, so a client
 *     can no longer set idStudent, a whole Department object or the
 *     "enrollments" collection through the body;
 *   - PUT takes the id from the PATH (/updateStudent/{id}), never from the body;
 *   - responses are DTOs too (no leaked entity graph, no infinite JSON recursion).
 */
@RestController
@RequestMapping("/students")
@CrossOrigin(origins = "http://localhost:4200")
@AllArgsConstructor
public class StudentController {

    private final IStudentService studentService;
    private final StudentMapper studentMapper;

    @GetMapping("/getAllStudents")
    public List<StudentDto> getAllStudents() {
        return studentService.getAllStudents()
                .stream()
                .map(studentMapper::toDto)
                .toList();
    }

    @GetMapping("/getStudent/{id}")
    public StudentDto getStudent(@PathVariable Long id) {
        return studentMapper.toDto(studentService.getStudentById(id));
    }

    @PostMapping("/createStudent")
    public StudentDto createStudent(@RequestBody StudentDto dto) {
        Student saved = studentService.saveStudent(studentMapper.toEntity(dto));
        return studentMapper.toDto(saved);
    }

    @PutMapping("/updateStudent/{id}")
    public StudentDto updateStudent(@PathVariable Long id, @RequestBody StudentDto dto) {
        Student existing = studentService.getStudentById(id);
        if (existing == null) {
            throw new RuntimeException("Student not found with id: " + id);
        }
        studentMapper.updateEntity(existing, dto);
        return studentMapper.toDto(studentService.saveStudent(existing));
    }

    @DeleteMapping("/deleteStudent/{id}")
    public void deleteStudent(@PathVariable Long id) {
        studentService.deleteStudent(id);
    }
}
