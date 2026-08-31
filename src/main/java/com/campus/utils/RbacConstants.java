package com.campus.utils;

import com.campus.dto.UserDTO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RbacConstants {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ACTIVITY_ADMIN = "ACTIVITY_ADMIN";
    public static final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";

    public static final String PERM_ACTIVITY_VIEW = "activity:view";
    public static final String PERM_ACTIVITY_SEARCH = "activity:search";
    public static final String PERM_ACTIVITY_CREATE = "activity:create";
    public static final String PERM_ACTIVITY_UPDATE = "activity:update";
    public static final String PERM_ACTIVITY_APPROVE = "activity:approve";
    public static final String PERM_ACTIVITY_OFFLINE = "activity:offline";
    public static final String PERM_ACTIVITY_MANAGE_CATEGORY = "activity:manage_category";
    public static final String PERM_REGISTRATION_CREATE = "registration:create";
    public static final String PERM_REGISTRATION_CANCEL = "registration:cancel";
    public static final String PERM_REGISTRATION_VIEW_SELF = "registration:view_self";
    public static final String PERM_REGISTRATION_VIEW_ALL = "registration:view_all";
    public static final String PERM_REGISTRATION_EXPORT = "registration:export";
    public static final String PERM_CHECKIN_VIEW_SELF = "checkin:view_self";
    public static final String PERM_CHECKIN_OPEN = "checkin:open";
    public static final String PERM_CHECKIN_CLOSE = "checkin:close";
    public static final String PERM_CHECKIN_VERIFY = "checkin:verify";
    public static final String PERM_CHECKIN_VIEW_RECORDS = "checkin:view_records";
    public static final String PERM_CHECKIN_EXPORT = "checkin:export";
    public static final String PERM_NOTIFICATION_VIEW_SELF = "notification:view_self";
    public static final String PERM_NOTIFICATION_SEND_ACTIVITY = "notification:send_activity";
    public static final String PERM_NOTIFICATION_PUBLISH_PLATFORM = "notification:publish_platform";
    public static final String PERM_PLATFORM_USER_MANAGE = "platform:user_manage";
    public static final String PERM_PLATFORM_ROLE_ASSIGN = "platform:role_assign";
    public static final String PERM_PLATFORM_STATISTICS_VIEW = "platform:statistics:view";
    public static final String PERM_PLATFORM_AUDITLOG_VIEW = "platform:auditlog:view";

    private RbacConstants() {
    }

    public static List<String> fallbackRoleCodes(Integer legacyRoleType) {
        if (legacyRoleType != null && legacyRoleType == UserDTO.ROLE_ORGANIZER) {
            return Collections.singletonList(ROLE_ACTIVITY_ADMIN);
        }
        return Collections.singletonList(ROLE_USER);
    }

    public static List<String> fallbackPermissions(List<String> roleCodes) {
        Set<String> permissions = new LinkedHashSet<>();
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Collections.emptyList();
        }
        if (roleCodes.contains(ROLE_USER)) {
            permissions.addAll(Arrays.asList(
                    PERM_ACTIVITY_VIEW,
                    PERM_ACTIVITY_SEARCH,
                    PERM_REGISTRATION_CREATE,
                    PERM_REGISTRATION_CANCEL,
                    PERM_REGISTRATION_VIEW_SELF,
                    PERM_CHECKIN_VIEW_SELF,
                    PERM_NOTIFICATION_VIEW_SELF
            ));
        }
        if (roleCodes.contains(ROLE_ACTIVITY_ADMIN)) {
            permissions.addAll(Arrays.asList(
                    PERM_ACTIVITY_VIEW,
                    PERM_ACTIVITY_SEARCH,
                    PERM_ACTIVITY_CREATE,
                    PERM_ACTIVITY_UPDATE,
                    PERM_REGISTRATION_VIEW_ALL,
                    PERM_REGISTRATION_EXPORT,
                    PERM_CHECKIN_OPEN,
                    PERM_CHECKIN_CLOSE,
                    PERM_CHECKIN_VERIFY,
                    PERM_CHECKIN_VIEW_RECORDS,
                    PERM_CHECKIN_EXPORT,
                    PERM_NOTIFICATION_SEND_ACTIVITY
            ));
        }
        if (roleCodes.contains(ROLE_PLATFORM_ADMIN)) {
            permissions.addAll(Arrays.asList(
                    PERM_ACTIVITY_VIEW,
                    PERM_ACTIVITY_APPROVE,
                    PERM_ACTIVITY_OFFLINE,
                    PERM_ACTIVITY_MANAGE_CATEGORY,
                    PERM_PLATFORM_USER_MANAGE,
                    PERM_PLATFORM_ROLE_ASSIGN,
                    PERM_PLATFORM_STATISTICS_VIEW,
                    PERM_PLATFORM_AUDITLOG_VIEW,
                    PERM_NOTIFICATION_PUBLISH_PLATFORM
            ));
        }
        return new ArrayList<>(permissions);
    }

    public static int resolveRoleType(List<String> roleCodes, Integer legacyRoleType) {
        if (roleCodes != null && roleCodes.contains(ROLE_ACTIVITY_ADMIN)) {
            return UserDTO.ROLE_ORGANIZER;
        }
        if (legacyRoleType != null) {
            return legacyRoleType;
        }
        return UserDTO.ROLE_STUDENT;
    }
}
