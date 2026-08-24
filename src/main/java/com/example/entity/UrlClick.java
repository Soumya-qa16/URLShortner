package com.example.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "url_clicks", indexes = {
    @Index(name = "idx_click_short_key", columnList = "shortKey")
})
public class UrlClick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String shortKey;

    private String ipAddress;
    private String userAgent;

    @Column(nullable = false)
    private LocalDateTime clickedAt;

    public UrlClick() {
    }

    public UrlClick(String shortKey, String ipAddress, String userAgent, LocalDateTime clickedAt) {
        this.shortKey = shortKey;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.clickedAt = clickedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getShortKey() {
        return shortKey;
    }

    public void setShortKey(String shortKey) {
        this.shortKey = shortKey;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public LocalDateTime getClickedAt() {
        return clickedAt;
    }

    public void setClickedAt(LocalDateTime clickedAt) {
        this.clickedAt = clickedAt;
    }
}