package com.campus.utils;

import com.campus.dto.UserDTO;

public final class AuthorizationUtils {

    private AuthorizationUtils() {
    }

    public static boolean hasRole(UserDTO user, String roleCode) {
        return user != null && user.hasRole(roleCode);
    }

    public static boolean hasPermission(UserDTO user, String permissionCode) {
        return user != null && user.hasPermission(permissionCode);
    }
}
