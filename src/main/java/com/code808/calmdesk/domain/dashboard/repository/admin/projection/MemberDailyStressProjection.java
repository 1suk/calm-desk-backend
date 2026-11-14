package com.code808.calmdesk.domain.dashboard.repository.admin.projection;

public interface MemberDailyStressProjection {
    Long getMemberId();
    Long getDepartmentId();
    String getDepartmentName();
    String getMemberName();
    Double getAvgStress();
}
