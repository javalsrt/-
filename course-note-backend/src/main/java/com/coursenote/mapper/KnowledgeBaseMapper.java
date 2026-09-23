package com.coursenote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coursenote.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {

    /** 按课程ID和用户ID查找知识库（学生隔离，每个学生独立知识库） */
    @Select("SELECT * FROM knowledge_base WHERE course_id = #{courseId} AND user_id = #{userId} AND deleted = 0 LIMIT 1")
    KnowledgeBase findByCourseIdAndUserId(@Param("courseId") Long courseId, @Param("userId") Long userId);

    /** 按课程ID查找该课程下所有知识库（用于聚合课程级文档） */
    @Select("SELECT * FROM knowledge_base WHERE course_id = #{courseId} AND deleted = 0")
    List<KnowledgeBase> findByCourseId(@Param("courseId") Long courseId);

    /** 按用户ID查找所有知识库 */
    @Select("SELECT * FROM knowledge_base WHERE user_id = #{userId} AND deleted = 0")
    List<KnowledgeBase> findByUserId(@Param("userId") Long userId);
}
