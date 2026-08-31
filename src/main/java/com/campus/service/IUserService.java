package com.campus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.dto.LoginFormDTO;
import com.campus.dto.OrganizerApplyDTO;
import com.campus.dto.ReviewActionDTO;
import com.campus.dto.Result;
import com.campus.entity.User;

import jakarta.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result applyOrganizer(OrganizerApplyDTO dto);

    Result queryMyOrganizerApplication();

    Result queryOrganizerApplications(String status);

    Result reviewOrganizerApplication(Long id, ReviewActionDTO dto);

}
