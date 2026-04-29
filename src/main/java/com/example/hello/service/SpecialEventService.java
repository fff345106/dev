package com.example.hello.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.hello.dto.SpecialEventCreateRequest;
import com.example.hello.dto.SpecialEventListItemResponse;
import com.example.hello.entity.SpecialEvent;
import com.example.hello.repository.SpecialEventRepository;

import jakarta.transaction.Transactional;

@Service
public class SpecialEventService {

    private final SpecialEventRepository specialEventRepository;

    public SpecialEventService(SpecialEventRepository specialEventRepository) {
        this.specialEventRepository = specialEventRepository;
    }

    public List<SpecialEventListItemResponse> listEvents() {
        return specialEventRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(SpecialEventListItemResponse::fromEntity)
                .toList();
    }

    @Transactional
    public SpecialEventListItemResponse create(SpecialEventCreateRequest request) {
        SpecialEvent event = new SpecialEvent();
        event.setTitle(trimToNull(request.getTitle()));
        event.setDescription(trimToNull(request.getDesc()));
        event.setImageUrl(trimToNull(request.getImage()));
        event.setUrl(trimToNull(request.getUrl()));

        SpecialEvent saved = specialEventRepository.save(event);
        return SpecialEventListItemResponse.fromEntity(saved);
    }

    @Transactional
    public void delete(Long id) {
        specialEventRepository.deleteById(id);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
