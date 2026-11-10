package com.burgerlab.staff;

import com.burgerlab.auth.AppUserPrincipal;
import com.burgerlab.staff.dto.StaffStoreResponse;
import com.burgerlab.staff.dto.StaffWorkspaceResponse;
import com.burgerlab.store.StoreBusinessStatus;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffService {

    private final UserStoreMembershipMapper membershipMapper;

    public StaffService(UserStoreMembershipMapper membershipMapper) {
        this.membershipMapper = membershipMapper;
    }

    @Transactional(readOnly = true)
    public StaffWorkspaceResponse workspace(AppUserPrincipal principal) {
        List<StaffStoreResponse> stores = membershipMapper
                .selectStoresForUser(principal.id())
                .stream()
                .map(this::toResponse)
                .toList();
        return new StaffWorkspaceResponse(stores);
    }

    public boolean hasStoreAccess(long userId, long storeId) {
        return membershipMapper.countMembership(userId, storeId) > 0;
    }

    public void requireStoreAccess(long userId, long storeId) {
        if (!hasStoreAccess(userId, storeId)) {
            throw new com.burgerlab.common.error.ResourceNotFoundException(
                    "STORE_NOT_FOUND", "门店不存在或无权访问");
        }
    }

    private StaffStoreResponse toResponse(StaffStoreRow row) {
        StoreBusinessStatus status = StoreBusinessStatus.fromDatabase(row.getBusinessStatus());
        boolean accepting = Boolean.TRUE.equals(row.getAcceptingOrders());
        return new StaffStoreResponse(
                row.getId(),
                row.getCode(),
                row.getName(),
                row.getAddress(),
                status,
                accepting,
                status == StoreBusinessStatus.OPEN && accepting
        );
    }
}
