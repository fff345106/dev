package com.example.hello.service;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.example.hello.dto.SpecialEventCreateRequest;
import com.example.hello.dto.SpecialEventListItemResponse;
import com.example.hello.entity.SpecialEvent;
import com.example.hello.repository.SpecialEventRepository;

import jakarta.transaction.Transactional;

@Service
public class SpecialEventService {

    private static final String CACHE_KEY = "events::all";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final SpecialEventRepository specialEventRepository;
    private final RedisCacheService redisCacheService;

    public SpecialEventService(SpecialEventRepository specialEventRepository, RedisCacheService redisCacheService) {
        this.specialEventRepository = specialEventRepository;
        this.redisCacheService = redisCacheService;
    }

    public List<SpecialEventListItemResponse> listEvents() {
        List<SpecialEventListItemResponse> cached = redisCacheService.get(CACHE_KEY, new TypeReference<List<SpecialEventListItemResponse>>() {});
        if (cached != null) {
            return cached;
        }
        List<SpecialEventListItemResponse> result = specialEventRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(SpecialEventListItemResponse::fromEntity)
                .toList();
        if (result != null && CACHE_TTL != null) {
            redisCacheService.put(CACHE_KEY, result, CACHE_TTL);
        }
        return result;
    }

    @Transactional
    public SpecialEventListItemResponse create(SpecialEventCreateRequest request) {
        SpecialEvent event = new SpecialEvent();
        event.setTitle(trimToNull(request.getTitle()));
        event.setDescription(trimToNull(request.getDesc()));
        event.setImageUrl(trimToNull(request.getImage()));
        event.setUrl(trimToNull(request.getUrl()));

        SpecialEvent saved = specialEventRepository.save(event);
        redisCacheService.evict(CACHE_KEY);
        return SpecialEventListItemResponse.fromEntity(saved);
    }

    @Transactional
    public void delete(@NonNull Long id) {
        specialEventRepository.deleteById(id);
        redisCacheService.evict(CACHE_KEY);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
