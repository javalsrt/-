package com.coursenote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coursenote.entity.CourseSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface CourseSessionMapper extends BaseMapper<CourseSession> {

    @Select("SELECT * FROM course_session WHERE user_id = #{userId} AND deleted = 0 AND session_date = #{date} ORDER BY created_at")
    List<CourseSession> findByDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Select("SELECT cs.* FROM course_session cs " +
            "WHERE cs.course_id = #{courseId} AND cs.deleted = 0 " +
            "ORDER BY cs.session_date DESC, cs.created_at DESC")
    List<CourseSession> findByCourseId(@Param("courseId") Long courseId);

    @Select("SELECT * FROM course_session WHERE user_id = #{userId} AND deleted = 0 " +
            "AND session_date = #{date} AND actual_start_time IS NOT NULL " +
            "AND actual_end_time IS NULL ORDER BY actual_start_time DESC LIMIT 1")
    CourseSession findOngoingSession(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Select("SELECT * FROM course_session WHERE id = #{id} AND deleted = 0")
    CourseSession findById(@Param("id") Long id);
}
