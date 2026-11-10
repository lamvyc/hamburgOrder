package com.burgerlab.platform.health;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.burgerlab.platform.health.dto.LivenessResponse;
import com.burgerlab.platform.health.dto.SystemStatusResponse;
import com.burgerlab.platform.health.dto.SystemStatusResponse.ApplicationStatus;
import com.burgerlab.platform.health.dto.SystemStatusResponse.DatabaseStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(PlatformHealthController.class)
class PlatformHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlatformHealthService healthService;

    @Test
    void livenessReturnsUp() throws Exception {
        when(healthService.liveness()).thenReturn(new LivenessResponse(
                "UP", "burger-order-api", "test", OffsetDateTime.now(ZoneOffset.UTC), 12));

        mockMvc.perform(get("/api/system/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("burger-order-api"));
    }

    @Test
    void degradedDatabaseIsA503ButStillReturnsTheSnapshot() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        SystemStatusResponse response = new SystemStatusResponse(
                HealthState.DEGRADED,
                now,
                "trace-test-0002",
                new ApplicationStatus(HealthState.UP, "burger-order-api", "test", "21", 20),
                new DatabaseStatus(HealthState.DEGRADED, true, "MySQL", "8.4.13", 4,
                        false, null, null, "SCHEMA_NOT_READY", "数据库可连接，但迁移状态读取失败")
        );
        when(healthService.status()).thenReturn(response);

        mockMvc.perform(get("/api/system/status"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.application.status").value("UP"))
                .andExpect(jsonPath("$.database.connected").value(true))
                .andExpect(jsonPath("$.database.schemaReady").value(false))
                .andExpect(jsonPath("$.database.code").value("SCHEMA_NOT_READY"));
    }

    @Test
    void unexpectedFailureDoesNotLeakInternalDetails() throws Exception {
        when(healthService.liveness()).thenThrow(new IllegalStateException("secret SQL state"));

        mockMvc.perform(get("/api/system/liveness"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("服务暂时不可用，请稍后重试"));
    }
}
