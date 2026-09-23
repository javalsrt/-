package com.coursenote.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String openid;
    private String unionid;
    private String nickname;
    private String avatarUrl;
    private String phone;
    private String school;
    private String major;
    private String grade;
    private String semester;
    private java.time.LocalDate semesterStartDate;
    private Integer semesterWeeks;

    // ===== 教务账号字段（与 znxsgl 共表，用于"学号登录 + 微信绑定"）=====
    private String studentNo;      // 学号
    private String username;       // 登录用户名（学号/用户名均可登录）
    private String passwordHash;   // BCrypt 密码哈希（仅服务端校验使用，不对外返回）

    @JsonIgnore
    public String getPasswordHash() { return passwordHash; }

    private String realName;       // 真实姓名
    private Integer role;          // 角色(1学生 2教师 3管理员)
    private Long classId;          // 班级ID
    private Integer status;        // 账号状态(0禁用 1正常)

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
