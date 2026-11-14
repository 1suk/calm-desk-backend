package com.code808.calmdesk.domain.dashboard.repository.admin.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.code808.calmdesk.domain.dashboard.repository.admin.cache.DashboardCache.*;

@Repository
@RequiredArgsConstructor
public class DashboardRedisRepository {

    private final StringRedisTemplate redisTemplate;

    public record AvgCount(double sum, long count) {
        public double avg() {
            return count == 0 ? 0.0 : sum / count;
        }
    }


    public void applyCheckinDelta(Long departmentId, Long companyId, LocalDate date,
                                  Long memberId, Double oldAvg, double newAvg) {
        double delta = newAvg - (oldAvg == null ? 0.0 : oldAvg);
        boolean isNewMember = (oldAvg == null);

        String deptKey = deptHashKey(departmentId, date);
        String companyKey = companyHashKey(companyId, date);
        String membersZsetKey = companyMembersZsetKey(companyId, date);

        redisTemplate.opsForHash().increment(deptKey, FIELD_SUM, delta);
        redisTemplate.opsForHash().increment(companyKey, FIELD_SUM, delta);

        if (isNewMember) {
            redisTemplate.opsForHash().increment(deptKey, FIELD_COUNT, 1L);
            redisTemplate.opsForHash().increment(companyKey, FIELD_COUNT, 1L);
        }

        redisTemplate.opsForZSet().add(membersZsetKey, memberId.toString(), newAvg);

        redisTemplate.expire(deptKey, TTL);
        redisTemplate.expire(companyKey, TTL);
        redisTemplate.expire(membersZsetKey, TTL);
    }

    public Optional<AvgCount> getDeptAvgCount(Long departmentId, LocalDate date) {
        return readAvgCount(deptHashKey(departmentId, date));
    }

    public Optional<AvgCount> getCompanyAvgCount(Long companyId, LocalDate date) {
        return readAvgCount(companyHashKey(companyId, date));
    }

    private Optional<AvgCount> readAvgCount(String key) {
        List<Object> values = redisTemplate.opsForHash()
                .multiGet(key, List.of(FIELD_SUM, FIELD_COUNT));

        if (values.get(0) == null || values.get(1) == null) {
            return Optional.empty();
        }

        double sum = Double.parseDouble(values.get(0).toString());
        long count = Long.parseLong(values.get(1).toString());
        return Optional.of(new AvgCount(sum, count));
    }

    //고위험군 수
    public Optional<Long> getHighRiskCount(Long companyId, LocalDate date, double threshold) {
        String key = companyMembersZsetKey(companyId, date);

        if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
            return Optional.empty();
        }

        Long count = redisTemplate.opsForZSet().count(key, threshold, Double.MAX_VALUE);

        return Optional.of(count == null ? 0L : count);
    }


    public Optional<Set<ZSetOperations.TypedTuple<String>>> getTopMembers(
            Long companyId, LocalDate date, double threshold, int limit) {

        String key = companyMembersZsetKey(companyId, date);

        if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
            return Optional.empty();
        }

        Set<ZSetOperations.TypedTuple<String>> result = redisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, threshold, Double.MAX_VALUE, 0, limit);

        return Optional.of(result);
    }
}