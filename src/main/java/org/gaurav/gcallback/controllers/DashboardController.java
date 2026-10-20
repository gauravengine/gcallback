package org.gaurav.gcallback.controllers;

import org.gaurav.gcallback.dto.ApiResponse;
import org.gaurav.gcallback.dto.DashboardStatsResponse;
import org.gaurav.gcallback.services.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private Clock clock;

    @Autowired
    @Qualifier("workerId")
    private String currentWorkerId;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getStats()));
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("service", "gcallback-distributed-scheduler");
        health.put("currentWorkerId", currentWorkerId);
        health.put("serverTime", clock.instant());
        health.put("timeZone", clock.getZone().getId());
        return ResponseEntity.ok(ApiResponse.ok(health));
    }
}
