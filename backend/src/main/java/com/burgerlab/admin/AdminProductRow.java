package com.burgerlab.admin;

import java.math.BigDecimal;

public class AdminProductRow {

    private Long id;
    private Long storeId;
    private String code;
    private String name;
    private String categoryName;
    private BigDecimal price;
    private Boolean onSale;
    private Integer version;
    private Integer available;
    private Integer reserved;
    private Integer stockVersion;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Boolean getOnSale() { return onSale; }
    public void setOnSale(Boolean onSale) { this.onSale = onSale; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Integer getAvailable() { return available; }
    public void setAvailable(Integer available) { this.available = available; }
    public Integer getReserved() { return reserved; }
    public void setReserved(Integer reserved) { this.reserved = reserved; }
    public Integer getStockVersion() { return stockVersion; }
    public void setStockVersion(Integer stockVersion) { this.stockVersion = stockVersion; }
}
