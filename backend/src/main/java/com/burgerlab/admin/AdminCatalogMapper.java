package com.burgerlab.admin;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdminCatalogMapper {

    @Select("""
            SELECT p.id, p.store_id, p.product_code AS code, p.name,
                   p.category_name, p.price, p.on_sale, p.version,
                   s.available, s.reserved, s.version AS stock_version
              FROM products p
              JOIN stock s ON s.product_id = p.id AND s.store_id = p.store_id
             WHERE p.store_id = #{storeId}
             ORDER BY p.category_sort, p.sort_order, p.id
            """)
    List<AdminProductRow> selectProducts(@Param("storeId") long storeId);

    @Select("""
            <script>
            SELECT p.id, p.store_id, p.product_code AS code, p.name,
                   p.category_name, p.price, p.on_sale, p.version,
                   s.available, s.reserved, s.version AS stock_version
              FROM products p
             <choose>
               <when test="onSale != null">
                 FORCE INDEX (idx_products_store_onsale_id)
               </when>
               <otherwise>
                 FORCE INDEX (idx_products_store_id)
               </otherwise>
             </choose>
              JOIN stock s ON s.product_id = p.id AND s.store_id = p.store_id
             WHERE p.store_id = #{storeId}
             <if test="afterId != null">
               AND p.id &gt; #{afterId}
             </if>
             <if test="onSale != null">
               AND p.on_sale = #{onSale}
             </if>
             ORDER BY p.id
             LIMIT #{limit}
            </script>
            """)
    List<AdminProductRow> selectProductPage(
            @Param("storeId") long storeId,
            @Param("afterId") Long afterId,
            @Param("onSale") Boolean onSale,
            @Param("limit") int limit
    );

    @Select("""
            SELECT p.id, p.store_id, p.product_code AS code, p.name,
                   p.category_name, p.price, p.on_sale, p.version,
                   s.available, s.reserved, s.version AS stock_version
              FROM products p
              JOIN stock s ON s.product_id = p.id AND s.store_id = p.store_id
             WHERE p.id = #{productId}
            """)
    AdminProductRow selectProduct(@Param("productId") long productId);

    @Update("""
            UPDATE products
               SET price = #{price}, on_sale = #{onSale}, version = version + 1
             WHERE id = #{productId} AND version = #{version}
            """)
    int updateProduct(
            @Param("productId") long productId,
            @Param("price") BigDecimal price,
            @Param("onSale") boolean onSale,
            @Param("version") int version
    );

    @Update("""
            UPDATE stock
               SET available = available + #{delta}, version = version + 1
             WHERE store_id = #{storeId}
               AND product_id = #{productId}
               AND available + #{delta} >= 0
            """)
    int adjustStockUnversioned(
            @Param("storeId") long storeId,
            @Param("productId") long productId,
            @Param("delta") int delta
    );

    @Update("""
            UPDATE stock
               SET available = available + #{delta}, version = version + 1
             WHERE store_id = #{storeId}
               AND product_id = #{productId}
               AND version = #{version}
               AND available + #{delta} >= 0
            """)
    int adjustStock(
            @Param("storeId") long storeId,
            @Param("productId") long productId,
            @Param("delta") int delta,
            @Param("version") int version
    );
}
