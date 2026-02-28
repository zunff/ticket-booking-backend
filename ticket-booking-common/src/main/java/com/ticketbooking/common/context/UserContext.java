package com.ticketbooking.common.context;

public class UserContext {
    private static final ThreadLocal<UserInfo> USER_INFO = new ThreadLocal<>();
    
    public static void setUserInfo(UserInfo userInfo) {
        USER_INFO.set(userInfo);
    }
    
    public static UserInfo getUserInfo() {
        return USER_INFO.get();
    }
    
    public static void setUserId(Long userId) {
        UserInfo userInfo = getUserInfo();
        if (userInfo == null) {
            userInfo = UserInfo.builder().userId(userId).build();
        } else {
            userInfo.setUserId(userId);
        }
        USER_INFO.set(userInfo);
    }
    
    public static Long getUserId() {
        UserInfo userInfo = getUserInfo();
        return userInfo != null ? userInfo.getUserId() : null;
    }
    
    public static void setUsername(String username) {
        UserInfo userInfo = getUserInfo();
        if (userInfo == null) {
            userInfo = UserInfo.builder().username(username).build();
        } else {
            userInfo.setUsername(username);
        }
        USER_INFO.set(userInfo);
    }
    
    public static String getUsername() {
        UserInfo userInfo = getUserInfo();
        return userInfo != null ? userInfo.getUsername() : null;
    }
    
    public static void setToken(String token) {
        UserInfo userInfo = getUserInfo();
        if (userInfo == null) {
            userInfo = UserInfo.builder().token(token).build();
        } else {
            userInfo.setToken(token);
        }
        USER_INFO.set(userInfo);
    }
    
    public static String getToken() {
        UserInfo userInfo = getUserInfo();
        return userInfo != null ? userInfo.getToken() : null;
    }
    
    public static void clear() {
        USER_INFO.remove();
    }
}
