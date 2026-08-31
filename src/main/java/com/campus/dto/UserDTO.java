package com.campus.dto;

import cn.hutool.core.collection.CollUtil;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UserDTO {
    public static final int ROLE_ORGANIZER = 1;
    public static final int ROLE_STUDENT = 2;

    private Long id;
    private String nickName;
    private String icon;
    private Integer roleType;
    private List<String> roleCodes = new ArrayList<>();
    private List<String> permissions = new ArrayList<>();

    public boolean hasRole(String roleCode) {
        return CollUtil.isNotEmpty(roleCodes) && roleCodes.contains(roleCode);
    }

    public boolean hasPermission(String permissionCode) {
        return CollUtil.isNotEmpty(permissions) && permissions.contains(permissionCode);
    }
}
