package com.burgerlab.admin;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("admin_stock_batch_lines")
public class AdminStockBatchLineEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long productId;
    private Integer delta;
    private String reason;
    private Integer availableBefore;
    private Integer availableAfter;
    private Integer stockVersionAfter;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Integer getDelta() { return delta; }
    public void setDelta(Integer delta) { this.delta = delta; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getAvailableBefore() { return availableBefore; }
    public void setAvailableBefore(Integer availableBefore) { this.availableBefore = availableBefore; }
    public Integer getAvailableAfter() { return availableAfter; }
    public void setAvailableAfter(Integer availableAfter) { this.availableAfter = availableAfter; }
    public Integer getStockVersionAfter() { return stockVersionAfter; }
    public void setStockVersionAfter(Integer stockVersionAfter) { this.stockVersionAfter = stockVersionAfter; }
}
