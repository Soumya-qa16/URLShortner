package com.example.service;

import com.example.entity.UrlClick;
import com.example.repository.UrlClickRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AnalyticsService {

    private final UrlClickRepository clickRepository;

    public AnalyticsService(UrlClickRepository clickRepository) {
        this.clickRepository = clickRepository;
    }

    @Async
    public void logClickAsync(String shortKey, String ipAddress, String userAgent) {
        UrlClick click = new UrlClick(shortKey, ipAddress, userAgent, LocalDateTime.now());
        clickRepository.save(click);
    }
}