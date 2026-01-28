package me.asikeida.ashore;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import me.asikeida.ashore.web.PingController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SpringBootTest
class AshoreApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void pingReturnsUnifiedRespShape() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PingController()).build();
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(0))
                .andExpect(jsonPath("$.result.app").value("ashore"))
                .andExpect(jsonPath("$.result.time").isNotEmpty());
    }
}
