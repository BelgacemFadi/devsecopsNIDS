package tn.esprit.studentmanagement.controllers;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tn.esprit.studentmanagement.dtos.DepartmentDto;
import tn.esprit.studentmanagement.dtos.DepartmentMapper;
import tn.esprit.studentmanagement.entities.Department;
import tn.esprit.studentmanagement.services.IDepartmentService;

import java.util.List;

/**
 * Fixed for Sonar S4684 (Mass assignment, CWE-915):
 *   - request bodies are DepartmentDto records, not the JPA entity,
 *     so a client can no longer set idDepartment or the "students" collection;
 *   - PUT takes the id from the PATH (/updateDepartment/{id}), never from the body;
 *   - responses are DTOs too (no leaked entity graph, no infinite JSON recursion).
 * NOTE: the "/Depatment" typo is kept on purpose for frontend compatibility.
 */
@RestController
@RequestMapping("/Depatment")
@CrossOrigin(origins = "http://localhost:4200")
@AllArgsConstructor
public class DepartmentController {

    private final IDepartmentService departmentService;
    private final DepartmentMapper departmentMapper;

    @GetMapping("/getAllDepartment")
    public List<DepartmentDto> getAllDepartment() {
        return departmentService.getAllDepartments()
                .stream()
                .map(departmentMapper::toDto)
                .toList();
    }

    @GetMapping("/getDepartment/{id}")
    public DepartmentDto getDepartment(@PathVariable Long id) {
        return departmentMapper.toDto(departmentService.getDepartmentById(id));
    }

    @PostMapping("/createDepartment")
    public DepartmentDto createDepartment(@RequestBody DepartmentDto dto) {
        Department saved = departmentService.saveDepartment(departmentMapper.toEntity(dto));
        return departmentMapper.toDto(saved);
    }

    @PutMapping("/updateDepartment/{id}")
    public DepartmentDto updateDepartment(@PathVariable Long id, @RequestBody DepartmentDto dto) {
        Department existing = departmentService.getDepartmentById(id);
        departmentMapper.updateEntity(existing, dto);
        return departmentMapper.toDto(departmentService.saveDepartment(existing));
    }

    @DeleteMapping("/deleteDepartment/{id}")
    public void deleteDepartment(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
    }
}
