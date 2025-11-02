package com.kt.social.domain.react.service.impl;

import com.kt.social.domain.react.model.ReactType;
import com.kt.social.domain.react.repository.ReactTypeRepository;
import com.kt.social.domain.react.service.ReactTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReactTypeServiceImpl implements ReactTypeService {

    private final ReactTypeRepository reactTypeRepository;

    @Override
    public ReactType getById(Long id) {
        return reactTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ReactType not found"));
    }

    @Override
    public List<ReactType> getAllTargetTypes() {
        return reactTypeRepository.findAll();
    }

    @Override
    public ReactType createReactType(ReactType reactType) {
        return reactTypeRepository.save(reactType);
    }

    @Override
    public ReactType updateReactType(ReactType reactType) {
        return reactTypeRepository.save(reactType);
    }

    @Override
    public void deleteReactType(ReactType reactType) {
        reactTypeRepository.delete(reactType);
    }

    @Override
    public void deleteReactTypeById(Long id) {
        ReactType reactType = getById(id);
        reactTypeRepository.delete(reactType);
    }
}
