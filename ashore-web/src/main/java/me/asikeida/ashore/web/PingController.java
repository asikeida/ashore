package me.asikeida.ashore.web;

import java.time.Instant;
import me.asikeida.ashore.api.resp.ResVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    /** 存活探针数据 */
    public record PingVo(String app, String time) {
    }

    @GetMapping("/api/ping")
    public ResVo<PingVo> ping() {
        return ResVo.ok(new PingVo("ashore", Instant.now().toString()));
    }
}
