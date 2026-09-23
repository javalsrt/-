package com.coursenote;

import com.znxsgl.config.DistinctBeanNameGenerator;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// scanBasePackages 覆盖 com.coursenote（小林）与 com.znxsgl（aiStudy 鉴权）两个包。
// @MapperScan 同时扫描两个 Mapper 包，并用 DistinctBeanNameGenerator 给 com.znxsgl 的 Mapper
// 加 "znxsgl" 前缀，避免 UserMapper/CourseMapper 等同名接口的 Bean 名冲突。
@SpringBootApplication(scanBasePackages = {"com.coursenote", "com.znxsgl"})
@MapperScan(value = {"com.coursenote.mapper", "com.znxsgl.mapper"},
        nameGenerator = DistinctBeanNameGenerator.class)
public class CourseNoteApplication {

    public static void main(String[] args) {
        SpringApplication.run(CourseNoteApplication.class, args);
    }
}