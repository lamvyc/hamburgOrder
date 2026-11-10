package com.burgerlab.inventory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("stock_logs")
public class StockLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long operatorUserId;
    private Long storeId;
    private Long productId;
    private String changeType;
    private Integer availableDelta;
    private Integer reservedDelta;
    private Integer availableAfter;
    private Integer reservedAfter;
    private String reason;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public Integer getAvailableDelta() {
        return availableDelta;
    }

    public void setAvailableDelta(Integer availableDelta) {
        this.availableDelta = availableDelta;
    }

    public Integer getReservedDelta() {
        return reservedDelta;
    }

    public void setReservedDelta(Integer reservedDelta) {
        this.reservedDelta = reservedDelta;
    }

    public Integer getAvailableAfter() {
        return availableAfter;
    }

    public void setAvailableAfter(Integer availableAfter) {
        this.availableAfter = availableAfter;
    }

    public Integer getReservedAfter() {
        return reservedAfter;
    }

    public void setReservedAfter(Integer reservedAfter) {
        this.reservedAfter = reservedAfter;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
