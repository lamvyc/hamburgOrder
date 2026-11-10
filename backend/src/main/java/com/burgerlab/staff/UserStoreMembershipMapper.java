package com.burgerlab.staff;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserStoreMembershipMapper {

    /**
     * The user_id predicate is the data-scope boundary. It is intentionally
     * explicit SQL rather than a client-supplied store id lookup.
     */
    @Select("""
            SELECT s.id,
                   s.code,
                   s.name,
                   s.address,
                   s.business_status,
                   s.accepting_orders
              FROM user_store_memberships m
              JOIN stores s ON s.id = m.store_id
             WHERE m.user_id = #{userId}
             ORDER BY s.id
            """)
    List<StaffStoreRow> selectStoresForUser(long userId);

    @Select("""
            SELECT COUNT(*)
              FROM user_store_memberships
             WHERE user_id = #{userId}
               AND store_id = #{storeId}
            """)
    int countMembership(@Param("userId") long userId, @Param("storeId") long storeId);
}
