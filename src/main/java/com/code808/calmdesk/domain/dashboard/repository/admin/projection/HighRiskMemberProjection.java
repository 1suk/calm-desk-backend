package com.code808.calmdesk.domain.dashboard.repository.admin.projection;

import java.time.LocalDate;

public interface HighRiskMemberProjection {
    Long getMemberId();
    String getMemberName();
    String getDepartmentName();
    Double getAvgStressLevel();
}
