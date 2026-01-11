package com.teletrack.userservice.util;

import com.teletrack.commonutils.dto.response.UserResponse;
import com.teletrack.userservice.entity.User;
import com.teletrack.userservice.mapper.UserMapper;
import com.teletrack.userservice.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class Utils {
    private final UserMapper userMapper;

    public UserResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        return userMapper.mapToUserResponse(user);
    }
}
