package com.campus.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.campus.dto.LoginFormDTO;
import com.campus.dto.OrganizerApplyDTO;
import com.campus.dto.Result;
import com.campus.dto.ReviewActionDTO;
import com.campus.dto.UserDTO;
import com.campus.dto.UserPreferenceTagUpdateDTO;
import com.campus.dto.UserProfileUpdateDTO;
import com.campus.entity.User;
import com.campus.entity.UserInfo;
import com.campus.service.OssService;
import com.campus.service.IActivityService;
import com.campus.service.IUserInfoService;
import com.campus.service.IUserService;
import com.campus.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import java.util.concurrent.TimeUnit;

import static com.campus.utils.RedisConstants.LOGIN_USER_KEY;
import static com.campus.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private IActivityService activityService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private OssService ossService;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 发送短信验证码并保存验证码
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // 实现登录功能
        return userService.login(loginForm, session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(@RequestHeader(value = "authentication", required = false) String token){
        if (StrUtil.isNotBlank(token)) {
            stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        }
        return Result.ok();
    }

    @GetMapping("/me")
    public Result me(){
        // 获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        syncUserProfileFromLoginState(user);
        return Result.ok(user);
    }

    @PostMapping("/avatar") //更新头像
    public Result uploadAvatar(@RequestHeader(value = "authentication", required = false) String token,
                               @RequestParam("file") MultipartFile file) {
        Long userId = UserHolder.getUser().getId();
        String url = ossService.uploadAvatar(userId, file);

        // 写回用户表（头像字段沿用 tb_user.icon）
        userService.updateById(new User().setId(userId).setIcon(url));

        // 同步登录态（避免刷新前端仍显示旧头像）
        syncLoginUserCache(token, "icon", url);

        return Result.ok(url);
    }

    @PutMapping("/profile")//更新用户信息
    public Result updateProfile(@RequestHeader(value = "authentication", required = false) String token,
                                @RequestBody UserProfileUpdateDTO dto) {
        Long userId = UserHolder.getUser().getId();

        if (dto != null && StrUtil.isNotBlank(dto.getNickName())) {
            userService.updateById(new User().setId(userId).setNickName(dto.getNickName()));
            syncLoginUserCache(token, "nickName", dto.getNickName());
        }

        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            info = new UserInfo();
            info.setUserId(userId);
        }
        if (dto != null) {
            if (dto.getIntroduce() != null) info.setIntroduce(dto.getIntroduce());
            if (dto.getGender() != null) info.setGender(dto.getGender());
            if (dto.getCity() != null) info.setCity(dto.getCity());
            if (dto.getBirthday() != null) info.setBirthday(dto.getBirthday());
            if (dto.getCollege() != null) info.setCollege(dto.getCollege());
            if (dto.getGrade() != null) info.setGrade(dto.getGrade());
            if (dto.getMentor() != null) info.setMentor(dto.getMentor());
        }
        userInfoService.saveOrUpdate(info);
        return Result.ok();
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        // 查询详情
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }

    @PostMapping("/organizer/apply")
    public Result applyOrganizer(@RequestBody OrganizerApplyDTO dto) {
        return userService.applyOrganizer(dto);
    }

    @GetMapping("/organizer/apply/me")
    public Result queryMyOrganizerApplication() {
        return userService.queryMyOrganizerApplication();
    }

    @GetMapping("/admin/organizer-applications")
    public Result queryOrganizerApplications(@RequestParam(value = "status", required = false) String status) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null || !currentUser.hasPermission("platform:user_manage")) {
            return Result.fail("无权查看主办方申请");
        }
        return userService.queryOrganizerApplications(status);
    }

    @PostMapping("/admin/organizer-applications/{id}/review")
    public Result reviewOrganizerApplication(@PathVariable("id") Long id, @RequestBody ReviewActionDTO dto) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null || !currentUser.hasPermission("platform:user_manage")) {
            return Result.fail("无权审核主办方申请");
        }
        return userService.reviewOrganizerApplication(id, dto);
    }

    @GetMapping("/preferences/activity-tags")
    public Result queryMyPreferenceTags() {
        return activityService.queryMyPreferenceTags();
    }

    @PutMapping("/preferences/activity-tags")
    public Result updateMyPreferenceTags(@RequestBody(required = false) UserPreferenceTagUpdateDTO dto) {
        return activityService.updateMyPreferenceTags(dto);
    }

    private void syncLoginUserCache(String token, String field, String value) {
        if (StrUtil.isBlank(token) || StrUtil.isBlank(field) || value == null) {
            return;
        }
        String key = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().put(key, field, value);
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
    }

    private void syncUserProfileFromLoginState(UserDTO user) {
        if (user == null || user.getId() == null) {
            return;
        }
        User persisted = userService.getById(user.getId());
        if (persisted == null) {
            return;
        }
        User update = new User().setId(user.getId());
        boolean changed = false;
        if (StrUtil.isNotBlank(user.getIcon()) && !StrUtil.equals(user.getIcon(), persisted.getIcon())) {
            update.setIcon(user.getIcon());
            changed = true;
        }
        if (StrUtil.isNotBlank(user.getNickName()) && !StrUtil.equals(user.getNickName(), persisted.getNickName())) {
            update.setNickName(user.getNickName());
            changed = true;
        }
        if (changed) {
            userService.updateById(update);
        }
    }
}
