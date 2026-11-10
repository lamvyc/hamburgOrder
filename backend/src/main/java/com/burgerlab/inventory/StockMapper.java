package com.burgerlab.inventory;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StockMapper {

    /**
     * The only place that reserves stock. Both sides of the invariant change
     * atomically, and available >= quantity is part of the predicate so no
     * read-then-write race can oversell.
     */
    @Update("""
            UPDATE stock
               SET available = available - #{quantity},
                   reserved = reserved + #{quantity},
                   version = version + 1
             WHERE store_id = #{storeId}
               AND product_id = #{productId}
               AND available >= #{quantity}
            """)
    int reserve(
            @Param("storeId") long storeId,
            @Param("productId") long productId,
            @Param("quantity") int quantity
    );

    @Update("""
            UPDATE stock
               SET reserved = reserved - #{quantity},
                   version = version + 1
             WHERE store_id = #{storeId}
               AND product_id = #{productId}
               AND reserved >= #{quantity}
            """)
    int commitReservation(
            @Param("storeId") long storeId,
            @Param("productId") long productId,
            @Param("quantity") int quantity
    );

    @Update("""
            UPDATE stock
               SET available = available + #{quantity},
                   reserved = reserved - #{quantity},
                   version = version + 1
             WHERE store_id = #{storeId}
               AND product_id = #{productId}
               AND reserved >= #{quantity}
            """)
    int releaseReservation(
            @Param("storeId") long storeId,
            @Param("productId") long productId,
            @Param("quantity") int quantity
    );

    @Select("""
            SELECT available, reserved, version
              FROM stock
             WHERE store_id = #{storeId}
               AND product_id = #{productId}
            """)
    StockSnapshot select(
            @Param("storeId") long storeId,
            @Param("productId") long productId
    );
}
