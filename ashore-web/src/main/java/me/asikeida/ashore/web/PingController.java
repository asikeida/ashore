package me.asikeida.ashore.web;

import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    public record PingResponse(String app, String status, String time) {
    }

    @GetMapping("/api/ping")
    public PingResponse ping() {
        return new PingResponse("ashore", "UP", Instant.now().toString());
    }
}
