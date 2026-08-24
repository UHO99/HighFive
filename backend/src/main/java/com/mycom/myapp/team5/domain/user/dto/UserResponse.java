package com.mycom.myapp.team5.domain.user.dto;

import com.mycom.myapp.team5.domain.user.entity.User;
import com.mycom.myapp.team5.global.common.util.MaskingUtils;

/**
 * 사용자 응답 DTO. 개인정보는 from()에서 마스킹된 값만 담는다.
 */
public record UserResponse(
        Long id,
        String email,
        String name,
        String phone
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                MaskingUtils.maskEmail(user.getEmail()),
                MaskingUtils.maskName(user.getName()),
                MaskingUtils.maskPhone(user.getPhone())
        );
    }
}
