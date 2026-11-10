package com.burgerlab.coupon;

import com.burgerlab.auth.AppUserPrincipal;
import com.burgerlab.coupon.dto.CouponTemplateResponse;
import com.burgerlab.coupon.dto.UserCouponResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping("/available")
    public List<CouponTemplateResponse> available(
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return couponService.available(principal.id());
    }

    @GetMapping("/mine")
    public List<UserCouponResponse> mine(
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return couponService.mine(principal.id());
    }

    @PostMapping("/{templateId}/claim")
    public ResponseEntity<UserCouponResponse> claim(
            @PathVariable("templateId") long templateId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(couponService.claim(templateId, principal.id()));
    }
}
