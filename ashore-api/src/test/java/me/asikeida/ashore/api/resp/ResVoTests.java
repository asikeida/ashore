package me.asikeida.ashore.api.resp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResVoTests {

    @Test
    void okWithoutResult() {
        ResVo<Void> vo = ResVo.ok();
        assertThat(vo.getStatus().getCode()).isZero();
        assertThat(vo.getResult()).isNull();
    }

    @Test
    void okWithResult() {
        ResVo<String> vo = ResVo.ok("data");
        assertThat(vo.getStatus().getCode()).isZero();
        assertThat(vo.getResult()).isEqualTo("data");
    }

    @Test
    void failWithEnum() {
        ResVo<Void> vo = ResVo.fail(StatusEnum.UNAUTHORIZED);
        assertThat(vo.getStatus().getCode()).isEqualTo(401001);
        assertThat(vo.getStatus().getMsg()).isEqualTo("未登录或登录已失效");
        // 枚举被归一化为 record，防止 Jackson 把枚举序列化成字符串
        assertThat(vo.getStatus()).isInstanceOf(Status.SimpleStatus.class);
    }

    @Test
    void failWithDynamicStatus() {
        ResVo<Void> vo = ResVo.fail(Status.of(404002, "面经不存在"));
        assertThat(vo.getStatus().getCode()).isEqualTo(404002);
        assertThat(vo.getResult()).isNull();
    }
}
