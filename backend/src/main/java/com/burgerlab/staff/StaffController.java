package com.burgerlab.staff;

import com.burgerlab.auth.AppUserPrincipal;
import com.burgerlab.staff.dto.StaffWorkspaceResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping("/workspace")
    public StaffWorkspaceResponse workspace(
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return staffService.workspace(principal);
    }
}
