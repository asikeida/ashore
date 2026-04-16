package me.asikeida.ashore.service.interact.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDate;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import me.asikeida.ashore.api.enums.RankActionEnum;
import me.asikeida.ashore.api.vo.rank.RankItemVo;
import me.asikeida.ashore.service.interact.service.RankService;
import me.asikeida.ashore.service.user.repository.entity.UserDO;
import me.asikeida.ashore.service.user.repository.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * 排行实现：日/月双 zset + Lua 脚本原子化。
 * <p>Lua 一次往返完成「正动作去重 → 双榜加分 → TTL 判设（==-1 才 EXPIRE）」，
 * 消除 paicoding「先判 TTL 再 EXPIRE 两步非原子」的竞态（fixme 修复②）。
 * 取消动作要求当日已有正记录（ acted 哈希），并回撤正记录允许重新获得。</p>
 */
@Service
public class RankServiceImpl implements RankService {

    private static final Logger log = LoggerFactory.getLogger(RankServiceImpl.class);

    private static final String DAILY_KEY_PREFIX = "ashore:rank:daily:";
    private static final String MONTHLY_KEY_PREFIX = "ashore:rank:monthly:";
    private static final String ACTED_KEY_PREFIX = "ashore:rank:acted:";
    private static final long DAILY_TTL_SECONDS = 31L * 24 * 3600;
    private static final long MONTHLY_TTL_SECONDS = 366L * 24 * 3600;

    /**
     * KEYS[1]=日榜 KEYS[2]=月榜 KEYS[3]=acted 哈希
     * ARGV[1]=用户 uid ARGV[2]=增量(可负) ARGV[3]=幂等字段 ARGV[4]=日榜TTL ARGV[5]=月榜TTL
     */
    private static final String APPLY_SCRIPT = """
            local delta = tonumber(ARGV[2])
            if delta > 0 then
              if redis.call('HEXISTS', KEYS[3], ARGV[3]) == 1 then return 0 end
              redis.call('HSET', KEYS[3], ARGV[3], '1')
            else
              if redis.call('HEXISTS', KEYS[3], ARGV[3]) == 0 then return 0 end
              redis.call('HDEL', KEYS[3], ARGV[3])
            end
            redis.call('ZINCRBY', KEYS[1], ARGV[2], ARGV[1])
            redis.call('ZINCRBY', KEYS[2], ARGV[2], ARGV[1])
            if redis.call('TTL', KEYS[1]) == -1 then redis.call('EXPIRE', KEYS[1], ARGV[4]) end
            if redis.call('TTL', KEYS[2]) == -1 then redis.call('EXPIRE', KEYS[2], ARGV[5]) end
            return 1
            """;

    private final StringRedisTemplate redisTemplate;

    private final UserMapper userMapper;

    private final DefaultRedisScript<Long> applyScript;

    public RankServiceImpl(StringRedisTemplate redisTemplate, UserMapper userMapper) {
        this.redisTemplate = redisTemplate;
        this.userMapper = userMapper;
        this.applyScript = new DefaultRedisScript<>(APPLY_SCRIPT, Long.class);
    }

    @Override
    public void record(Long userId, RankActionEnum action, Long articleId) {
        try {
            LocalDate today = LocalDate.now();
            String actedField = action.isCancel()
                    ? "act:" + action.positiveCounterpart().name() + ":" + articleId
                    : "act:" + action.name() + ":" + articleId;
            redisTemplate.execute(applyScript,
                    List.of(DAILY_KEY_PREFIX + today, MONTHLY_KEY_PREFIX + java.time.YearMonth.from(today).toString(),
                            ACTED_KEY_PREFIX + userId + ":" + today),
                    String.valueOf(userId), String.valueOf(action.getScore()), actedField,
                    String.valueOf(DAILY_TTL_SECONDS), String.valueOf(MONTHLY_TTL_SECONDS));
        } catch (Exception e) {
            // 排行是旁路：Redis 不可达时仅告警，不反压业务主流程
            log.warn("排行记录失败 action={} user={}: {}", action, userId, e.getMessage());
        }
    }

    @Override
    public List<RankItemVo> dailyTop(int limit) {
        return top(DAILY_KEY_PREFIX + LocalDate.now(), limit);
    }

    @Override
    public List<RankItemVo> monthlyTop(int limit) {
        return top(MONTHLY_KEY_PREFIX + java.time.YearMonth.from(LocalDate.now()).toString(), limit);
    }

    private List<RankItemVo> top(String key, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, safeLimit - 1L);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = tuples.stream().map(t -> Long.parseLong(t.getValue())).toList();
        Map<Long, String> nicknames = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserDO::getId,
                        u -> u.getNickname() == null ? "岸友" : u.getNickname(),
                        (a, b) -> a));

        List<RankItemVo> result = new ArrayList<>();
        int rank = 1;
        for (var tuple : tuples) {
            long uid = Long.parseLong(tuple.getValue());
            result.add(new RankItemVo(rank++, String.valueOf(uid),
                    nicknames.getOrDefault(uid, "岸友"), tuple.getScore() == null ? 0 : tuple.getScore()));
        }
        return result;
    }
}
