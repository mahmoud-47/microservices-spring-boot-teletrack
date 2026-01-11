package com.teletrack.userservice.mapper;

import com.teletrack.commonutils.dto.response.UserResponse;
import com.teletrack.userservice.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse mapToUserResponse(User user) {
        if(user == null){
            return null;
        }

        return UserResponse.builder()
                .id(user.getId().toString())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole().toString())
                .isActive(user.getActive())
                .isApproved(user.getApproved())
                .build();
    }

    public UserResponse toUserResponse(User user) {
        return mapToUserResponse(user);
    }
}
