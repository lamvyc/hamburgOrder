package com.burgerlab.platform.health;

import com.burgerlab.platform.health.dto.LivenessResponse;
import com.burgerlab.platform.health.dto.SystemStatusResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class PlatformHealthController {

    private final PlatformHealthService healthService;

    public PlatformHealthController(PlatformHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/liveness")
    public ResponseEntity<LivenessResponse> liveness() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(healthService.liveness());
    }

    /**
     * A degraded or unavailable database is a real service failure, so it must
     * not be disguised as HTTP 200. The body still carries the diagnostic
     * snapshot needed by the status page.
     */
    @GetMapping("/status")
    public ResponseEntity<SystemStatusResponse> status() {
        SystemStatusResponse status = healthService.status();
        HttpStatus httpStatus = status.status() == HealthState.UP
                ? HttpStatus.OK
                : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus)
                .cacheControl(CacheControl.noStore())
                .body(status);
    }
}
