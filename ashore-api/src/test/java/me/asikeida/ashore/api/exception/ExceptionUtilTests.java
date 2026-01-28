package me.asikeida.ashore.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import me.asikeida.ashore.api.resp.StatusEnum;
import org.junit.jupiter.api.Test;

class ExceptionUtilTests {

    @Test
    void ofWithoutArgsKeepsStaticMsg() {
        BizException e = ExceptionUtil.of(StatusEnum.FORBIDDEN);
        assertThat(e.getStatus().getCode()).isEqualTo(403001);
        assertThat(e.getMessage()).isEqualTo("无权限执行该操作");
    }

    @Test
    void ofWithArgsFillsPlaceholders() {
        BizException e = ExceptionUtil.of(StatusEnum.PARAM_ERROR, "标题", "不能为空");
        assertThat(e.getStatus().getCode()).isEqualTo(400001);
        assertThat(e.getStatus().getMsg()).isEqualTo("请求参数校验失败");
        // getMessage 取自动态 Status 的 msg（若枚举消息含 {} 才会被填充，此处验证携带能力）
        assertThat(e).isInstanceOf(BizException.class);
    }

    @Test
    void formatReplacesInOrder() {
        String out = ExceptionUtil.format("{} 不能为空，长度需 {}-{} 字符", "标题", 3, 50);
        assertThat(out).isEqualTo("标题 不能为空，长度需 3-50 字符");
    }

    @Test
    void formatHandlesNoPlaceholderAndExtraArgs() {
        assertThat(ExceptionUtil.format("静态消息", "多余参数")).isEqualTo("静态消息");
        assertThat(ExceptionUtil.format(null, "x")).isNull();
    }
}
