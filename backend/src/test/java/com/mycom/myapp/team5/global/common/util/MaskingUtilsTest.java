package com.mycom.myapp.team5.global.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingUtilsTest {

    @Test
    @DisplayName("이메일은 로컬 앞 글자만 남기고 마스킹한다")
    void maskEmail() {
        assertThat(MaskingUtils.maskEmail("hong@example.com")).isEqualTo("h***@example.com");
        assertThat(MaskingUtils.maskEmail("a@b.co")).isEqualTo("*@b.co");
        assertThat(MaskingUtils.maskEmail(null)).isNull();
    }

    @Test
    @DisplayName("전화번호는 앞 3자리와 뒤 4자리만 남긴다")
    void maskPhone() {
        assertThat(MaskingUtils.maskPhone("010-1234-5678")).isEqualTo("010-****-5678");
        assertThat(MaskingUtils.maskPhone("01012345678")).isEqualTo("010-****-5678");
        assertThat(MaskingUtils.maskPhone(null)).isNull();
    }

    @Test
    @DisplayName("이름은 첫 글자만 남기고 나머지를 마스킹한다")
    void maskName() {
        assertThat(MaskingUtils.maskName("홍길동")).isEqualTo("홍**");
        assertThat(MaskingUtils.maskName("김")).isEqualTo("*");
        assertThat(MaskingUtils.maskName(null)).isNull();
    }

    @Test
    @DisplayName("로그 문자열 안의 이메일·전화를 마스킹한다")
    void maskForLog() {
        String masked = MaskingUtils.maskForLog("user=hong@example.com phone=010-1234-5678");
        assertThat(masked).contains("h***@example.com");
        assertThat(masked).contains("010-****-5678");
        assertThat(masked).doesNotContain("hong@example.com");
        assertThat(masked).doesNotContain("1234");
    }
}
