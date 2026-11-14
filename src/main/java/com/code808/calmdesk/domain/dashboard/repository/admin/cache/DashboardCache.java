package com.code808.calmdesk.domain.dashboard.repository.admin.cache;

import java.time.LocalDate;
import java.time.Duration;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class DashboardCache {

    public static final Duration TTL = Duration.ofDays(10);

    public static final String FIELD_SUM = "sum";
    public static final String FIELD_COUNT = "count";

    public static String deptHashKey(Long departmentId, LocalDate date) {
        return "dashboard:dept:" + departmentId + ":" + date;
    }

    public static String companyHashKey(Long companyId, LocalDate date) {
        return "dashboard:company:" + companyId + ":" + date;
    }

    public static String companyMembersZsetKey(Long companyId, LocalDate date) {
        return "dashboard:company:" + companyId + ":" + date + ":members";
    }
}

