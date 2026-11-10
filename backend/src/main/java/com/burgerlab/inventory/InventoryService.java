package com.burgerlab.inventory;

import com.burgerlab.common.error.BusinessConflictException;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final StockMapper stockMapper;
    private final StockLogMapper stockLogMapper;

    public InventoryService(StockMapper stockMapper, StockLogMapper stockLogMapper) {
        this.stockMapper = stockMapper;
        this.stockLogMapper = stockLogMapper;
    }

    /**
     * Must join the order transaction. A separate transaction here would allow
     * an order to commit without its reservation, or vice versa.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(
            long orderId,
            long storeId,
            long productId,
            String productName,
            int quantity,
            LocalDateTime now
    ) {
        int affected = stockMapper.reserve(storeId, productId, quantity);
        if (affected != 1) {
            throw new BusinessConflictException(
                    "STOCK_INSUFFICIENT", "商品库存不足：" + productName);
        }

        StockSnapshot after = stockMapper.select(storeId, productId);
        if (after == null) {
            throw new IllegalStateException("Stock row disappeared after conditional update");
        }

        StockLogEntity log = new StockLogEntity();
        log.setOrderId(orderId);
        log.setStoreId(storeId);
        log.setProductId(productId);
        log.setChangeType("RESERVE");
        log.setAvailableDelta(-quantity);
        log.setReservedDelta(quantity);
        log.setAvailableAfter(after.getAvailable());
        log.setReservedAfter(after.getReserved());
        log.setReason("ORDER_CREATED");
        log.setCreatedAt(now);
        stockLogMapper.insert(log);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseReservation(
            long orderId,
            long storeId,
            long productId,
            int quantity,
            String reason,
            LocalDateTime now
    ) {
        int affected = stockMapper.releaseReservation(storeId, productId, quantity);
        if (affected != 1) {
            throw new BusinessConflictException(
                    "STOCK_RELEASE_BROKEN", "库存释放数据不一致，取消已回滚");
        }

        StockSnapshot after = stockMapper.select(storeId, productId);
        if (after == null) {
            throw new IllegalStateException("Stock row disappeared after reservation release");
        }

        StockLogEntity log = new StockLogEntity();
        log.setOrderId(orderId);
        log.setStoreId(storeId);
        log.setProductId(productId);
        log.setChangeType("RELEASE");
        log.setAvailableDelta(quantity);
        log.setReservedDelta(-quantity);
        log.setAvailableAfter(after.getAvailable());
        log.setReservedAfter(after.getReserved());
        log.setReason(reason);
        log.setCreatedAt(now);
        stockLogMapper.insert(log);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void commitReservation(
            long orderId,
            long storeId,
            long productId,
            int quantity,
            LocalDateTime now
    ) {
        int affected = stockMapper.commitReservation(storeId, productId, quantity);
        if (affected != 1) {
            throw new BusinessConflictException(
                    "STOCK_RESERVATION_BROKEN", "库存预占数据不一致，支付已回滚");
        }

        StockSnapshot after = stockMapper.select(storeId, productId);
        if (after == null) {
            throw new IllegalStateException("Stock row disappeared after reservation commit");
        }

        StockLogEntity log = new StockLogEntity();
        log.setOrderId(orderId);
        log.setStoreId(storeId);
        log.setProductId(productId);
        log.setChangeType("COMMIT");
        log.setAvailableDelta(0);
        log.setReservedDelta(-quantity);
        log.setAvailableAfter(after.getAvailable());
        log.setReservedAfter(after.getReserved());
        log.setReason("PAYMENT_CONFIRMED");
        log.setCreatedAt(now);
        stockLogMapper.insert(log);
    }
}
