package com.campus.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UserProfileUpdateDTO {
    /**
     * 用户昵称（tb_user.nick_name）
     */
    private String nickName;

    /**
     * 个人介绍（tb_user_info.introduce）
     */
    private String introduce;

    /**
     * 性别：false=男(0), true=女(1)
     */
    private Boolean gender;

    /**
     * 城市
     */
    private String city;

    /**
     * 生日
     */
    private LocalDate birthday;

    /**
     * 所属学院
     */
    private String college;

    /**
     * 所属年级
     */
    private String grade;

    /**
     * 导师
     */
    private String mentor;
}

