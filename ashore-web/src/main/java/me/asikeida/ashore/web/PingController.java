package me.asikeida.ashore.web;

import java.time.Instant;
import me.asikeida.ashore.api.resp.ResVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统", description = "存活探针与系统端点")
@RestController
public class PingController {

    /** 存活探针数据 */
    public record PingVo(String app, String time) {
    }

    @Operation(summary = "存活探针", description = "应用存活检查；不鉴权")
    @GetMapping("/api/ping")
    public ResVo<PingVo> ping() {
        return ResVo.ok(new PingVo("ashore", Instant.now().toString()));
    }
}
