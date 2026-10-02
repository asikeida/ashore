package me.asikeida.ashore.service.profile.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * 魔数识别单测。事故背景（2026-11-09）：JPEG 分支的 d[1]==0xD8 忘加 &0xFF 掩码，
 * byte 有符号比较恒 false——真实 JPEG 全部被拒（用户上传 1MB jpg 报 400004）。
 * 本测试用四种格式的标准文件头字节锁定行为，正负值场景都覆盖。
 */
class DetectExtTests {

    private String detect(byte[] data) throws Exception {
        MeServiceImpl service = new MeServiceImpl(
                null, null, null, null, null, null, null, null, 2, 5);
        Method m = MeServiceImpl.class.getDeclaredMethod("detectExt", byte[].class);
        m.setAccessible(true);
        return (String) m.invoke(service, (Object) data);
    }

    @Test
    void jpegMagicWithHighBytesIsRecognized() throws Exception {
        // FF D8 FF FE（JPEG SOI + COM 段）——含 >0x7F 的字节，回归事故场景
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xFE, 0x1D, 0x0B, 0, 0};
        assertThat(detect(jpg)).isEqualTo("jpg");
    }

    @Test
    void pngMagicIsRecognized() throws Exception {
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        assertThat(detect(png)).isEqualTo("png");
    }

    @Test
    void webpMagicIsRecognized() throws Exception {
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        assertThat(detect(webp)).isEqualTo("webp");
    }

    @Test
    void gifMagicIsRecognized() throws Exception {
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a'};
        assertThat(detect(gif)).isEqualTo("gif");
    }

    @Test
    void unknownMagicReturnsNull() throws Exception {
        byte[] exe = {0x4D, 0x5A, (byte) 0x90, 0x00};   // PE 可执行文件头
        assertThat(detect(exe)).isNull();
        byte[] heic = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'};  // HEIC
        assertThat(detect(heic)).isNull();
    }
}
