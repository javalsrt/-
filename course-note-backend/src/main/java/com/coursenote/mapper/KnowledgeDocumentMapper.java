package com.coursenote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coursenote.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

    @Select("SELECT * FROM knowledge_document WHERE knowledge_base_id = #{kbId} AND deleted = 0 ORDER BY created_at DESC")
    List<KnowledgeDocument> findByKnowledgeBaseId(@Param("kbId") Long kbId);

    @Select("SELECT * FROM knowledge_document WHERE knowledge_base_id = #{kbId} AND user_id = #{userId} AND deleted = 0 ORDER BY created_at DESC")
    List<KnowledgeDocument> findByKnowledgeBaseIdAndUserId(@Param("kbId") Long kbId, @Param("userId") Long userId);

    @Select("SELECT * FROM knowledge_document WHERE material_id = #{materialId} AND deleted = 0")
    KnowledgeDocument findByMaterialId(@Param("materialId") Long materialId);

    @Select("SELECT * FROM knowledge_document WHERE material_id = #{materialId} AND user_id = #{userId} AND deleted = 0 LIMIT 1")
    KnowledgeDocument findByMaterialIdAndUserId(@Param("materialId") Long materialId, @Param("userId") Long userId);

    /** 批量统计每个学生的知识库文档数 */
    @Select("<script>" +
            "SELECT user_id AS userId, COUNT(*) AS cnt FROM knowledge_document " +
            "WHERE deleted = 0 " +
            "<if test='userIds != null and userIds.size() > 0'>" +
            "AND user_id IN " +
            "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</if>" +
            " GROUP BY user_id" +
            "</script>")
    List<Map<String, Object>> countByUserIds(@Param("userIds") List<Long> userIds);

    /** 按日期统计某个学生的知识库文档上传数 */
    @Select("SELECT DATE(created_at) AS date, COUNT(*) AS cnt FROM knowledge_document " +
            "WHERE user_id = #{userId} AND deleted = 0 " +
            "AND created_at >= #{startDate} AND created_at < #{endDate} " +
            "GROUP BY DATE(created_at)")
    List<Map<String, Object>> countByUserIdAndDate(@Param("userId") Long userId,
                                                   @Param("startDate") String startDate,
                                                   @Param("endDate") String endDate);
}
