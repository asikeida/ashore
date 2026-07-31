package me.asikeida.ashore.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 并发测试的 Redis 可用性探针（仅测试源，不参与打包）。
 * <p>门控语义升级（2026-11-08）：此前仅探测端口监听——隧道半死（端口在、转发死）
 * 或密码缺失时测试照样开跑，最终以 NOAUTH 堆栈收场。现在探针执行完整
 * AUTH + PING 握手：密码解析自环境变量或仓库 .env（与 dev profile 的
 * config.import 同源），任何一步失败即判定不可用，调用方据此优雅跳过。</p>
 */
public final class RedisTunnelProbe {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 16379;
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 3000;

    private RedisTunnelProbe() {
    }

    /** 端口可达且完成（可选）AUTH + PING 握手才返回 true */
    public static boolean available() {
        String password = resolvePassword();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            // 有密码先 AUTH 再 PING（内联命令一次写入，Redis 顺序回包）
            String payload = (password == null || password.isBlank())
                    ? "PING\r\n"
                    : "AUTH " + password + "\r\nPING\r\n";
            socket.getOutputStream().write(payload.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            return readReply(socket).contains("+PONG");
        } catch (Exception e) {
            return false;
        }
    }

    /** 循环读回复直到出现 +PONG / 对端关闭 / 读超时——单次 read 可能只拿到 AUTH 的 +OK */
    private static String readReply(Socket socket) throws IOException {
        StringBuilder reply = new StringBuilder();
        byte[] buf = new byte[64];
        while (reply.indexOf("+PONG") < 0) {
            int n = socket.getInputStream().read(buf);
            if (n < 0) {
                break;
            }
            reply.append(new String(buf, 0, n, StandardCharsets.UTF_8));
        }
        return reply.toString();
    }

    /**
     * 密码解析顺序：环境变量（手工 export 场景优先）→ 仓库 .env。
     * 测试工作目录随执行器不同（Maven=模块目录，IDEA 可能为仓库根），两处都找。
     */
    private static String resolvePassword() {
        String fromEnv = System.getenv("ASHORE_REDIS_PASSWORD");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        for (Path candidate : new Path[]{Path.of(".env"), Path.of("../.env")}) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try {
                for (String line : Files.readAllLines(candidate, StandardCharsets.UTF_8)) {
                    if (line.startsWith("ASHORE_REDIS_PASSWORD=")) {
                        return line.substring("ASHORE_REDIS_PASSWORD=".length()).trim();
                    }
                }
            } catch (IOException ignored) {
                // 读不到当无密码处理，由握手结果决定跳过与否
            }
        }
        return null;
    }
}
