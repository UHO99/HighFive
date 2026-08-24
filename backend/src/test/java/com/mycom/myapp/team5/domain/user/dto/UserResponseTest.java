package com.mycom.myapp.team5.domain.user.dto;

import com.mycom.myapp.team5.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserResponseTest {

    @Test
    @DisplayName("from()은 개인정보를 마스킹한 응답을 만든다")
    void fromMasksPersonalInfo() {
        User user = User.builder()
                .email("hong@example.com")
                .name("홍길동")
                .phone("010-1234-5678")
                .build();

        UserResponse response = UserResponse.from(user);

        assertThat(response.email()).isEqualTo("h***@example.com");
        assertThat(response.name()).isEqualTo("홍**");
        assertThat(response.phone()).isEqualTo("010-****-5678");
    }
}
