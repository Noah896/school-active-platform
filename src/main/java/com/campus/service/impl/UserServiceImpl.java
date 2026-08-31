package com.campus.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.dto.LoginFormDTO;
import com.campus.dto.OrganizerApplyDTO;
import com.campus.dto.Result;
import com.campus.dto.ReviewActionDTO;
import com.campus.dto.UserDTO;
import com.campus.entity.OrganizerApplication;
import com.campus.entity.SysPermission;
import com.campus.entity.SysRole;
import com.campus.entity.SysRolePermission;
import com.campus.entity.SysUserRole;
import com.campus.entity.User;
import com.campus.mapper.OrganizerApplicationMapper;
import com.campus.mapper.SysPermissionMapper;
import com.campus.mapper.SysRoleMapper;
import com.campus.mapper.SysRolePermissionMapper;
import com.campus.mapper.SysUserRoleMapper;
import com.campus.mapper.UserMapper;
import com.campus.service.INotificationService;
import com.campus.service.IReviewRecordService;
import com.campus.service.IUserService;
import com.campus.utils.RegexUtils;
import com.campus.utils.AuthorizationUtils;
import com.campus.utils.RbacConstants;
import com.campus.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.campus.utils.RedisConstants.*;
import static com.campus.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private static final int ROLE_STUDENT = UserDTO.ROLE_STUDENT;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SysRoleMapper sysRoleMapper;

    @Resource
    private SysPermissionMapper sysPermissionMapper;

    @Resource
    private SysUserRoleMapper sysUserRoleMapper;

    @Resource
    private SysRolePermissionMapper sysRolePermissionMapper;

    @Resource
    private OrganizerApplicationMapper organizerApplicationMapper;

    @Resource
    private INotificationService notificationService;

    @Resource
    private IReviewRecordService reviewRecordService;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        // 3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        System.out.println("验证码为:" + code);
        // 4.保存验证码到 session
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 5.发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);
        // 返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String account = StrUtil.trim(loginForm.getPhone());
        if (StrUtil.isBlank(account)) {
            return Result.fail("手机号或账号不能为空");
        }
        User user;
        if (StrUtil.isNotBlank(loginForm.getPassword())) {
            user = queryPasswordLoginUser(account);
            if (user == null) {
                return Result.fail("账号不存在");
            }
            if (!StrUtil.equals(user.getPassword(), loginForm.getPassword())) {
                return Result.fail("密码错误");
            }
        } else {
            if (RegexUtils.isPhoneInvalid(account)) {
                return Result.fail("手机号格式错误！");
            }
            String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + account);
            String code = loginForm.getCode();
            if (cacheCode == null || !cacheCode.equals(code)) {
                return Result.fail("验证码错误");
            }
            user = query().eq("phone", account).one();
            if (user == null) {
                user = createUserWithPhone(account);
            }
        }

        // 7.保存用户信息到 redis中
        // 7.1.随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        // 7.2.将User对象转为HashMap存储
        UserDTO userDTO = buildLoginUser(user);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> {
                            if (fieldValue instanceof List) {
                                return String.join(",", (List<String>) fieldValue);
                            }
                            return fieldValue.toString();
                        }));
        // 7.3.存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 7.4.设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 8.返回token
        return Result.ok(token);
    }

    @Override
    public Result applyOrganizer(OrganizerApplyDTO dto) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }
        if (AuthorizationUtils.hasPermission(currentUser, RbacConstants.PERM_ACTIVITY_CREATE)
                || AuthorizationUtils.hasRole(currentUser, RbacConstants.ROLE_PLATFORM_ADMIN)) {
            return Result.fail("当前账号已具备活动管理权限，无需重复申请");
        }
        if (dto == null || StrUtil.isBlank(dto.getOrgName()) || StrUtil.isBlank(dto.getReason())) {
            return Result.fail("请填写完整的申请信息");
        }
        OrganizerApplication latest = organizerApplicationMapper.selectOne(new QueryWrapper<OrganizerApplication>()
                .eq("user_id", currentUser.getId())
                .orderByDesc("id")
                .last("limit 1"));
        if (latest != null && ("PENDING".equals(latest.getApplyStatus()) || "APPROVED".equals(latest.getApplyStatus()))) {
            return Result.fail("你已有进行中的申请或已通过申请");
        }
        OrganizerApplication application = new OrganizerApplication()
                .setUserId(currentUser.getId())
                .setApplyStatus("PENDING")
                .setOrgName(dto.getOrgName().trim())
                .setReason(dto.getReason().trim())
                .setReviewerId(null)
                .setReviewRemark(null)
                .setReviewTime(null);
        organizerApplicationMapper.insert(application);
        notificationService.notifyRole(
                RbacConstants.ROLE_PLATFORM_ADMIN,
                "有新的主办方申请",
                "用户提交了主办方申请，申请组织：“" + application.getOrgName() + "”。",
                "ORGANIZER_APPLY_PENDING",
                "ORGANIZER_APPLICATION",
                application.getId()
        );
        return Result.ok();
    }

    @Override
    public Result queryMyOrganizerApplication() {
        OrganizerApplication application = organizerApplicationMapper.selectOne(new QueryWrapper<OrganizerApplication>()
                .eq("user_id", UserHolder.getUser().getId())
                .orderByDesc("id")
                .last("limit 1"));
        return Result.ok(application);
    }

    @Override
    public Result queryOrganizerApplications(String status) {
        List<OrganizerApplication> records = organizerApplicationMapper.selectList(new QueryWrapper<OrganizerApplication>()
                .eq(StrUtil.isNotBlank(status), "apply_status", status)
                .orderByAsc("apply_status")
                .orderByDesc("create_time"));
        enrichOrganizerApplications(records);
        return Result.ok(records);
    }

    @Override
    public Result reviewOrganizerApplication(Long id, ReviewActionDTO dto) {
        if (id == null) {
            return Result.fail("申请ID不能为空");
        }
        if (dto == null || dto.getApproved() == null) {
            return Result.fail("审核结果不能为空");
        }
        OrganizerApplication application = organizerApplicationMapper.selectById(id);
        if (application == null) {
            return Result.fail("申请不存在");
        }
        if (!"PENDING".equals(application.getApplyStatus())) {
            return Result.fail("该申请已审核");
        }
        String targetStatus = Boolean.TRUE.equals(dto.getApproved()) ? "APPROVED" : "REJECTED";
        application.setApplyStatus(targetStatus);
        application.setReviewerId(UserHolder.getUser().getId());
        application.setReviewRemark(StrUtil.trim(dto.getReviewRemark()));
        application.setReviewTime(LocalDateTime.now());
        organizerApplicationMapper.updateById(application);
        if (Boolean.TRUE.equals(dto.getApproved())) {
            bindRole(application.getUserId(), RbacConstants.ROLE_ACTIVITY_ADMIN);
        }
            notifyOrganizerReviewResult(application, dto);
        String applicantName = queryUserNameMap(Collections.singletonList(application.getUserId()))
                .getOrDefault(application.getUserId(), "");
        reviewRecordService.record(
                RbacConstants.ROLE_PLATFORM_ADMIN,
                "PLATFORM_ADMIN",
                "ORGANIZER_APPLICATION",
                application.getId(),
                application.getOrgName(),
                application.getUserId(),
                applicantName,
                Boolean.TRUE.equals(dto.getApproved()) ? "APPROVED" : "REJECTED",
                dto.getReviewRemark()
        );
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        // 1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        user.setRoleType(ROLE_STUDENT);
        // 2.保存用户
        save(user);
        bindDefaultUserRole(user.getId());//绑定默认角色
        return user;
    }

    private User queryPasswordLoginUser(String account) {
        return query().and(wrapper -> wrapper.eq("phone", account).or().eq("username", account)).one();
    }

    private UserDTO buildLoginUser(User user) {
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        List<String> roleCodes = loadRoleCodes(user);
        List<String> permissions = loadPermissions(roleCodes, user.getRoleType());
        userDTO.setRoleCodes(roleCodes);
        userDTO.setPermissions(permissions);
        userDTO.setRoleType(RbacConstants.resolveRoleType(roleCodes, user.getRoleType()));
        return userDTO;
    }

    private List<String> loadRoleCodes(User user) {
        LinkedHashSet<String> roleCodes = new LinkedHashSet<>(RbacConstants.fallbackRoleCodes(user.getRoleType()));
        try {
            List<SysUserRole> relations = sysUserRoleMapper.selectList(new QueryWrapper<SysUserRole>()
                    .eq("user_id", user.getId()));
            if (relations == null || relations.isEmpty()) {
                return new ArrayList<>(roleCodes);
            }
            List<Long> roleIds = relations.stream()
                    .map(SysUserRole::getRoleId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (roleIds.isEmpty()) {
                return new ArrayList<>(roleCodes);
            }
            List<SysRole> roles = sysRoleMapper.selectList(new QueryWrapper<SysRole>()
                    .in("id", roleIds)
                    .eq("status", 1));
            roles.stream()
                    .map(SysRole::getRoleCode)
                    .filter(StrUtil::isNotBlank)
                    .forEach(roleCodes::add);
            return new ArrayList<>(roleCodes);
        } catch (Exception e) {
            log.warn("加载 RBAC 角色失败，回退 legacy role_type userId={}", user.getId(), e);
            return new ArrayList<>(roleCodes);
        }
    }

    private List<String> loadPermissions(List<String> roleCodes, Integer legacyRoleType) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return RbacConstants.fallbackPermissions(RbacConstants.fallbackRoleCodes(legacyRoleType));
        }
        try {
            List<SysRole> roles = sysRoleMapper.selectList(new QueryWrapper<SysRole>()
                    .in("role_code", roleCodes)
                    .eq("status", 1));
            List<Long> roleIds = roles.stream()
                    .map(SysRole::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (roleIds.isEmpty()) {
                return RbacConstants.fallbackPermissions(roleCodes);
            }
            List<SysRolePermission> relations = sysRolePermissionMapper.selectList(new QueryWrapper<SysRolePermission>()
                    .in("role_id", roleIds));
            List<Long> permissionIds = relations.stream()
                    .map(SysRolePermission::getPermissionId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (permissionIds.isEmpty()) {
                return RbacConstants.fallbackPermissions(roleCodes);
            }
            List<SysPermission> permissions = sysPermissionMapper.selectList(new QueryWrapper<SysPermission>()
                    .in("id", permissionIds)
                    .eq("status", 1));
            LinkedHashSet<String> codes = permissions.stream()
                    .map(SysPermission::getPermissionCode)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return codes.isEmpty() ? RbacConstants.fallbackPermissions(roleCodes) : new ArrayList<>(codes);
        } catch (Exception e) {
            log.warn("加载 RBAC 权限失败，回退默认权限 roleCodes={}", roleCodes, e);
            return RbacConstants.fallbackPermissions(roleCodes);
        }
    }

    private void bindDefaultUserRole(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            SysRole userRole = sysRoleMapper.selectOne(new QueryWrapper<SysRole>()
                    .eq("role_code", RbacConstants.ROLE_USER)
                    .eq("status", 1)
                    .last("limit 1"));
            if (userRole == null) {
                return;
            }
            Long exists = sysUserRoleMapper.selectCount(new QueryWrapper<SysUserRole>()
                    .eq("user_id", userId)
                    .eq("role_id", userRole.getId()));
            if (exists != null && exists > 0) {
                return;
            }
            sysUserRoleMapper.insert(new SysUserRole()
                    .setUserId(userId)
                    .setRoleId(userRole.getId()));
        } catch (Exception e) {
            log.warn("绑定默认 USER 角色失败，回退 legacy role_type userId={}", userId, e);
        }
    }

    private void bindRole(Long userId, String roleCode) {
        if (userId == null || StrUtil.isBlank(roleCode)) {
            return;
        }
        SysRole role = sysRoleMapper.selectOne(new QueryWrapper<SysRole>()
                .eq("role_code", roleCode)
                .eq("status", 1)
                .last("limit 1"));
        if (role == null) {
            return;
        }
        Long exists = sysUserRoleMapper.selectCount(new QueryWrapper<SysUserRole>()
                .eq("user_id", userId)
                .eq("role_id", role.getId()));
        if (exists != null && exists > 0) {
            return;
        }
        sysUserRoleMapper.insert(new SysUserRole().setUserId(userId).setRoleId(role.getId()));
    }

    private void notifyOrganizerReviewResult(OrganizerApplication application, ReviewActionDTO dto) {
        if (application == null || application.getUserId() == null || dto == null) {
            return;
        }
        boolean approved = Boolean.TRUE.equals(dto.getApproved());
        StringBuilder content = new StringBuilder();
        content.append("你的主办方申请“").append(application.getOrgName()).append("”")
                .append(approved ? "已审核通过，现在可以发起和管理活动。" : "未通过审核。");
        if (!approved && StrUtil.isNotBlank(dto.getReviewRemark())) {
            content.append("原因：").append(dto.getReviewRemark());
        }
        notificationService.notifyUsers(
                Collections.singletonList(application.getUserId()),
                approved ? "主办方申请已通过" : "主办方申请未通过",
                content.toString(),
                approved ? "ORGANIZER_APPLY_APPROVED" : "ORGANIZER_APPLY_REJECTED",
                "ORGANIZER_APPLICATION",
                application.getId()
        );
    }

    private Map<Long, String> queryUserNameMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickName, (a, b) -> a));
    }

    private void enrichOrganizerApplications(List<OrganizerApplication> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> userIds = records.stream().map(OrganizerApplication::getUserId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, User> userMap = listByIds(userIds).stream().collect(Collectors.toMap(User::getId, item -> item));
        for (OrganizerApplication record : records) {
            User applicant = userMap.get(record.getUserId());
            if (applicant == null) {
                continue;
            }
            record.setApplicantName(applicant.getNickName());
            record.setApplicantUsername(StrUtil.blankToDefault(applicant.getUsername(), applicant.getPhone()));
        }
    }
}
