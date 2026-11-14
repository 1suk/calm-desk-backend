package com.code808.calmdesk.domain.dashboard.service.admin;

import com.code808.calmdesk.domain.attendance.entity.Attendance;
import com.code808.calmdesk.domain.attendance.repository.AttendanceRepository;
import com.code808.calmdesk.domain.dashboard.dto.admin.DashboardDto;
import com.code808.calmdesk.domain.dashboard.repository.admin.DashboardRepository;
import com.code808.calmdesk.domain.dashboard.repository.admin.projection.AttendanceRateProjection;
import com.code808.calmdesk.domain.dashboard.repository.admin.projection.CompanyStatsProjection;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {
    private final DashboardRepository dashboardRepository;
    private final AttendanceRepository attendanceRepository;

    private static final int TOP_HIGH_RISK_LIMIT = 5;

    private static final List<Attendance.AttendanceStatus> attendanceStatuses = Arrays.asList(Attendance.AttendanceStatus.ATTEND, Attendance.AttendanceStatus.LATE);

    private LocalDate getBaseDate(DashboardDto.DashboardRequest request) {
        return Optional.ofNullable(request.getDate()).or(() -> attendanceRepository.findLatestWorkDate(request.getCompanyId())).orElseGet(LocalDate::now);
    }

    public List<DashboardDto.DepartmentStats> getDepartmentStats(DashboardDto.DashboardRequest request, LocalDate baseDate, LocalDateTime startOfDay, LocalDateTime endOfDay) {
        return dashboardRepository.findDepartmentStats(request.getCompanyId(), baseDate, startOfDay, endOfDay)
                .stream()
                .map(DashboardDto.DepartmentStats::of)
                .toList();
    }

    public List<DashboardDto.HighRiskMember> getHighRiskMembers(DashboardDto.DashboardRequest request, LocalDate baseDate) {
        return dashboardRepository.findHighRiskMembers(request.getCompanyId(), baseDate, request.getThreshold(), PageRequest.of(0, TOP_HIGH_RISK_LIMIT))
                .stream()
                .map(DashboardDto.HighRiskMember::of)
                .toList();
    }

    public DashboardDto.CompanyStats getCompanyStats(DashboardDto.DashboardRequest request, LocalDate baseDate) {
        Long companyId = request.getCompanyId();

        CompanyStatsProjection todayStats = dashboardRepository.findCompanyStats(companyId, baseDate, request.getThreshold());
        AttendanceRateProjection todayAttendance = dashboardRepository.findAttendanceRate(companyId, baseDate, attendanceStatuses);

        Long consultationCount = dashboardRepository.countWaitingConsultations(companyId);
        Long vacationCount = dashboardRepository.countPendingVacations(companyId);

        return DashboardDto.CompanyStats.of(todayStats.getAvgStressLevel(), todayStats.getTotalMembers(), todayStats.getHighRiskCount(), (todayAttendance != null) ? todayAttendance.getAttendanceRate() : 0.0, consultationCount, vacationCount);
    }

    @Override
    public DashboardDto.DashboardResponse getRealtimeStats(DashboardDto.DashboardRequest request) {

        LocalDate baseDate = getBaseDate(request);
        LocalDateTime startOfDay = baseDate.atStartOfDay();
        LocalDateTime endOfDay = baseDate.atTime(23, 59, 59);

        DashboardDto.CompanyStats companyStats = getCompanyStats(request, baseDate);
        List<DashboardDto.DepartmentStats> departmentStats = getDepartmentStats(request, baseDate, startOfDay, endOfDay);
        List<DashboardDto.HighRiskMember> highRiskMembers = getHighRiskMembers(request, baseDate);

        return DashboardDto.DashboardResponse.of(baseDate, companyStats, departmentStats, highRiskMembers);
    }


    @Override
    public DashboardDto.DashboardResponse getAllStats(DashboardDto.DashboardRequest request) {
        LocalDate baseDate = getBaseDate(request);
        LocalDateTime startOfDay = baseDate.atStartOfDay();
        LocalDateTime endOfDay = baseDate.atTime(23, 59, 59);

        LocalDate lastDate = attendanceRepository
                .findLatestWorkDateBefore(request.getCompanyId(), baseDate)
                .orElse(baseDate.minusDays(1));

        LocalDateTime startOfLastDay = lastDate.atStartOfDay();
        LocalDateTime endOfLastDay = lastDate.atTime(23, 59, 59);

        DashboardDto.CompanyStats companyStats = getCompanyStats(request, baseDate);

        List<DashboardDto.DepartmentStats> departmentStats =
                getDepartmentStats(request, baseDate, startOfDay, endOfDay);

        List<DashboardDto.HighRiskMember> highRiskMembers = getHighRiskMembers(request, baseDate);

        DashboardDto.CompanyStats lastCompanyStats = getCompanyStats(request, lastDate);

        List<DashboardDto.DepartmentStats> lastDepartmentStats =
                getDepartmentStats(request, lastDate, startOfLastDay, endOfLastDay);

        return DashboardDto.DashboardResponse.of(
                baseDate, companyStats, departmentStats, highRiskMembers,
                lastDate, lastCompanyStats, lastDepartmentStats);
    }
}
