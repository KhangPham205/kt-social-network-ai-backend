package com.kt.social.domain.react.controller;

import com.kt.social.domain.react.model.ReactType;
import com.kt.social.domain.react.repository.ReactTypeRepository;
import com.kt.social.domain.react.service.ReactTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/react-types")
@RequiredArgsConstructor
// @PreAuthorize("hasRole('ADMIN')")
public class ReactTypeAdminController {

    private final ReactTypeService reactTypeService;

    @GetMapping
    public ResponseEntity<List<ReactType>> all() {
        return ResponseEntity.ok(reactTypeService.getAllTargetTypes());
    }

    @PostMapping
    public ResponseEntity<ReactType> create(@RequestBody ReactType in) {
        return ResponseEntity.ok(reactTypeService.createReactType(in));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReactType> update(@PathVariable Long id, @RequestBody ReactType in) {
        ReactType existing = reactTypeService.getById(id);
        in.setId(existing.getId());
        return ResponseEntity.ok(reactTypeService.updateReactType(in));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reactTypeService.deleteReactTypeById(id);
        return ResponseEntity.noContent().build();
    }
}