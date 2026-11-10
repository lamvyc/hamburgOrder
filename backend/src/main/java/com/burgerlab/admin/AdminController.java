package com.burgerlab.admin;

import com.burgerlab.admin.dto.AdminEntryResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @GetMapping("/entry")
    public AdminEntryResponse entry() {
        return new AdminEntryResponse(
                "ADMIN",
                true,
                "管理员基础入口已启用；门店、商品、优惠与统计管理将在后续阶段接入。",
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }
}
