package com.coursenote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coursenote.entity.Material;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface MaterialMapper extends BaseMapper<Material> {

    @Select("SELECT * FROM material WHERE session_id = #{sessionId} AND deleted = 0 ORDER BY sort_order, created_at")
    List<Material> findBySessionId(@Param("sessionId") Long sessionId);

    @Select("SELECT * FROM material WHERE id = #{id} AND deleted = 0")
    Material findById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM material WHERE session_id = #{sessionId} AND deleted = 0")
    Integer countBySessionId(@Param("sessionId") Long sessionId);

    /** 批量统计每个学生的资料数 */
    @Select("<script>" +
            "SELECT user_id AS userId, COUNT(*) AS cnt FROM material " +
            "WHERE deleted = 0 " +
            "<if test='userIds != null and userIds.size() > 0'>" +
            "AND user_id IN " +
            "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</if>" +
            " GROUP BY user_id" +
            "</script>")
    List<Map<String, Object>> countByUserIds(@Param("userIds") List<Long> userIds);

    /** 按日期统计某个学生的资料上传数 */
    @Select("SELECT DATE(created_at) AS date, COUNT(*) AS cnt FROM material " +
            "WHERE user_id = #{userId} AND deleted = 0 " +
            "AND created_at >= #{startDate} AND created_at < #{endDate} " +
            "GROUP BY DATE(created_at)")
    List<Map<String, Object>> countByUserIdAndDate(@Param("userId") Long userId,
                                                   @Param("startDate") String startDate,
                                                   @Param("endDate") String endDate);
}
