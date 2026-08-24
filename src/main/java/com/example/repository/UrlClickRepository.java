package com.example.repository;


import com.example.entity.UrlClick;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UrlClickRepository extends JpaRepository<UrlClick, Long> {
    long countByShortKey(String shortKey);
    List<UrlClick> findByShortKey(String shortKey);
}

	