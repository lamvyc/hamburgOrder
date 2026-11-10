package com.burgerlab.store;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.burgerlab.common.error.ResourceNotFoundException;
import com.burgerlab.store.dto.StoreListResponse;
import com.burgerlab.store.dto.StoreResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreService {

    private final StoreMapper storeMapper;

    public StoreService(StoreMapper storeMapper) {
        this.storeMapper = storeMapper;
    }

    @Transactional(readOnly = true)
    public StoreListResponse listStores() {
        List<StoreEntity> entities = storeMapper.selectList(
                Wrappers.<StoreEntity>lambdaQuery().orderByAsc(StoreEntity::getId));
        return new StoreListResponse(entities.stream().map(this::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public StoreResponse getStore(long storeId) {
        StoreEntity entity = storeMapper.selectById(storeId);
        if (entity == null) {
            throw new ResourceNotFoundException("STORE_NOT_FOUND", "门店不存在");
        }
        return toResponse(entity);
    }

    private StoreResponse toResponse(StoreEntity entity) {
        StoreBusinessStatus businessStatus = StoreBusinessStatus.fromDatabase(entity.getBusinessStatus());
        boolean acceptingOrders = Boolean.TRUE.equals(entity.getAcceptingOrders());
        boolean orderable = businessStatus == StoreBusinessStatus.OPEN && acceptingOrders;

        return new StoreResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getAddress(),
                entity.getLatitude(),
                entity.getLongitude(),
                businessStatus,
                acceptingOrders,
                orderable,
                entity.getOpeningHours()
        );
    }
}
