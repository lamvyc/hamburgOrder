package com.burgerlab.admin;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminStockBatchLineMapper extends BaseMapper<AdminStockBatchLineEntity> {

    @Select("""
            SELECT * FROM admin_stock_batch_lines
             WHERE batch_id = #{batchId}
             ORDER BY product_id
            """)
    List<AdminStockBatchLineEntity> selectByBatch(@Param("batchId") long batchId);
}
