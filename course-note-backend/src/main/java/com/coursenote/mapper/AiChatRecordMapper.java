package com.coursenote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coursenote.entity.AiChatRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AiChatRecordMapper extends BaseMapper<AiChatRecord> {

    /** 按日期统计某个学生的 AI 提问数 */
    @Select("SELECT DATE(created_at) AS date, COUNT(*) AS cnt FROM ai_chat_record " +
            "WHERE user_id = #{userId} AND role = 'user' " +
            "AND created_at >= #{startDate} AND created_at < #{endDate} " +
            "GROUP BY DATE(created_at)")
    List<Map<String, Object>> countByUserIdAndDate(@Param("userId") Long userId,
                                                   @Param("startDate") String startDate,
                                                   @Param("endDate") String endDate);
}
