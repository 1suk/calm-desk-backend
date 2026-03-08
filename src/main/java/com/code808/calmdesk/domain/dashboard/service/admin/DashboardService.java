package com.code808.calmdesk.domain.dashboard.service.admin;

import com.code808.calmdesk.domain.dashboard.dto.admin.DashboardDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface DashboardService {
    List<DashboardDto.DepartmentStats> getDepartmentStats(DashboardDto.DashboardRequest request, LocalDate baseDate, LocalDateTime startOfDay, LocalDateTime endOfDay );
    List<DashboardDto.HighRiskMember> getHighRiskMembers(DashboardDto.DashboardRequest request, LocalDate baseDate);
    DashboardDto.CompanyStats getCompanyStats(DashboardDto.DashboardRequest request, LocalDate baseDate);
//    DashboardDto.CompanyStats getCompanyStats(DashboardDto.DashboardRequest request, LocalDate baseDate, Long consultationCount, Long vacationCount);
    DashboardDto.DashboardResponse getRealtimeStats(DashboardDto.DashboardRequest request);
    DashboardDto.DashboardResponse getYesterdayStats(DashboardDto.DashboardRequest request);
}
