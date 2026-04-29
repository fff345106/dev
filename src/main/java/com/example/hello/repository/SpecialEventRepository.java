package com.example.hello.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.hello.entity.SpecialEvent;

public interface SpecialEventRepository extends JpaRepository<SpecialEvent, Long> {

    List<SpecialEvent> findAllByOrderByCreatedAtDesc();
}
