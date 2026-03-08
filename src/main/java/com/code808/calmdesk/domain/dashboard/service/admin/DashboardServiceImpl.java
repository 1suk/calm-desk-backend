package com.code808.calmdesk.domain.dashboard.service.admin;

import com.code808.calmdesk.domain.attendance.entity.Attendance;
import com.code808.calmdesk.domain.attendance.entity.StressSummary;
import com.code808.calmdesk.domain.attendance.repository.AttendanceRepository;
import com.code808.calmdesk.domain.dashboard.dto.admin.DashboardDto;
import com.code808.calmdesk.domain.dashboard.repository.admin.DashboardRepository;
import com.code808.calmdesk.domain.dashboard.repository.admin.projection.AttendanceRateProjection;
import com.code808.calmdesk.domain.dashboard.repository.admin.projection.CompanyStatsProjection;
import com.code808.calmdesk.domain.dashboard.repository.admin.projection.DepartmentStatsProjection;
import com.code808.calmdesk.domain.dashboard.repository.admin.projection.HighRiskMemberProjection;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.StopWatch;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {
        private final DashboardRepository dashboardRepository;
        private final AttendanceRepository attendanceRepository;

        private static final List<Attendance.AttendanceStatus> attendanceStatuses = Arrays.asList(
                Attendance.AttendanceStatus.ATTEND,
                Attendance.AttendanceStatus.LATE);

        private LocalDate getBaseDate(DashboardDto.DashboardRequest request) {
                return Optional.ofNullable(request.getDate())
                                .or(() -> attendanceRepository.findLatestWorkDate(request.getCompanyId()))
                                .orElseGet(LocalDate::now);
        }

    public List<DashboardDto.DepartmentStats> getDepartmentStats(
            DashboardDto.DashboardRequest request, LocalDate baseDate,
            LocalDateTime startOfDay, LocalDateTime endOfDay) {

        List<DepartmentStatsProjection> projections = dashboardRepository
                .findDepartmentStats(request.getCompanyId(), baseDate, startOfDay, endOfDay);

        return projections.stream()
                .map(DashboardDto.DepartmentStats::of)
                .toList();
    }

    public List<DashboardDto.HighRiskMember> getHighRiskMembers(
            DashboardDto.DashboardRequest request, LocalDate baseDate) {

        Pageable top5 = PageRequest.of(0, 5);

        List<HighRiskMemberProjection> highRiskMembers = dashboardRepository.findHighRiskMembers(
                request.getCompanyId(),
                baseDate,
                request.getThreshold(),
                top5);

        return highRiskMembers.stream()
                .map(DashboardDto.HighRiskMember::of)
                .toList();
    }

    public DashboardDto.CompanyStats getCompanyStats(DashboardDto.DashboardRequest request, LocalDate baseDate
                                                     ) {
//                LocalDate baseDate = getBaseDate(request);

        CompanyStatsProjection todayStats = dashboardRepository.findCompanyStats(
                request.getCompanyId(), baseDate, request.getThreshold());

        AttendanceRateProjection todayAttendance = dashboardRepository.findAttendanceRate(
                request.getCompanyId(), baseDate, attendanceStatuses);

                Long consultationCount = dashboardRepository.countWaitingConsultations(request.getCompanyId());
                Long vacationCount = dashboardRepository.countPendingVacations(request.getCompanyId());

        return DashboardDto.CompanyStats.of(
                todayStats.getAvgStressLevel(),
                todayStats.getTotalMembers(),
                todayStats.getHighRiskCount(),
                (todayAttendance != null) ? todayAttendance.getAttendanceRate() : 0.0,
                consultationCount,
                vacationCount);
    }

//    public DashboardDto.DashboardResponse getAllStats(DashboardDto.DashboardRequest request) {
//
//        LocalDate baseDate = getBaseDate(request);
//        LocalDate lastDate = attendanceRepository
//                .findLatestWorkDateBefore(request.getCompanyId(), baseDate)
//                .orElse(baseDate);
//
//        LocalDateTime startOfDay = baseDate.atStartOfDay();
//        LocalDateTime endOfDay = baseDate.atTime(23, 59, 59);
//        LocalDateTime startOfLastDay = lastDate.atStartOfDay();
//        LocalDateTime endOfLastDay = lastDate.atTime(23, 59, 59);
//
//        Long consultationCount = dashboardRepository.countWaitingConsultations(request.getCompanyId());
//        Long vacationCount = dashboardRepository.countPendingVacations(request.getCompanyId());
//
//        DashboardDto.CompanyStats companyStats = getCompanyStats(request, baseDate, consultationCount, vacationCount);
//        DashboardDto.CompanyStats lastCompanyStats = getCompanyStats(request, lastDate, consultationCount, vacationCount);
//
//        List<DashboardDto.DepartmentStats> departmentStats = getDepartmentStats(request, baseDate, startOfDay, endOfDay);
//        List<DashboardDto.DepartmentStats> lastDepartmentStats = getDepartmentStats(request, lastDate, startOfLastDay, endOfLastDay);
//
//        List<DashboardDto.HighRiskMember> highRiskMembers = getHighRiskMembers(request, baseDate);
//
//        return DashboardDto.DashboardResponse.of(
//                baseDate, companyStats, lastCompanyStats,
//                lastDate, departmentStats, lastDepartmentStats, highRiskMembers);
//    }

//    @Override
//    public DashboardDto.DashboardResponse getRealtimeStats(DashboardDto.DashboardRequest request) {
//        LocalDate baseDate = getBaseDate(request);
//        LocalDateTime startOfDay = baseDate.atStartOfDay();
//        LocalDateTime endOfDay = baseDate.atTime(23, 59, 59);
//
//        Long consultationCount = dashboardRepository.countWaitingConsultations(request.getCompanyId());
//        Long vacationCount = dashboardRepository.countPendingVacations(request.getCompanyId());
//
//        DashboardDto.CompanyStats companyStats = getCompanyStats(request, baseDate, consultationCount, vacationCount);
//        List<DashboardDto.DepartmentStats> departmentStats = getDepartmentStats(request, baseDate, startOfDay, endOfDay);
//        List<DashboardDto.HighRiskMember> highRiskMembers = getHighRiskMembers(request, baseDate);
//
//        return DashboardDto.DashboardResponse.of(baseDate, companyStats, departmentStats, highRiskMembers);
//    }

    @Override
    public DashboardDto.DashboardResponse getRealtimeStats(DashboardDto.DashboardRequest request) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start("parallel");

        LocalDate baseDate = getBaseDate(request);
        LocalDateTime startOfDay = baseDate.atStartOfDay();
        LocalDateTime endOfDay = baseDate.atTime(23, 59, 59);

        CompletableFuture<DashboardDto.CompanyStats> companyFuture =
                CompletableFuture.supplyAsync(() -> getCompanyStats(request, baseDate));

        CompletableFuture<List<DashboardDto.DepartmentStats>> departmentFuture =
                CompletableFuture.supplyAsync(() -> getDepartmentStats(request, baseDate, startOfDay, endOfDay));

        CompletableFuture<List<DashboardDto.HighRiskMember>> highRiskFuture =
                CompletableFuture.supplyAsync(() -> getHighRiskMembers(request, baseDate));

        CompletableFuture.allOf(companyFuture, departmentFuture, highRiskFuture).join();

        stopWatch.stop();
        log.info("StopWatch realtime: {} seconds\n{}", stopWatch.getTotalTimeSeconds(), stopWatch.prettyPrint());

        return DashboardDto.DashboardResponse.of(
                baseDate,
                companyFuture.join(),
                departmentFuture.join(),
                highRiskFuture.join());
    }

//    @Override
//    public DashboardDto.DashboardResponse getYesterdayStats(DashboardDto.DashboardRequest request) {
//        LocalDate baseDate = getBaseDate(request);
//        LocalDate lastDate = attendanceRepository
//                .findLatestWorkDateBefore(request.getCompanyId(), baseDate)
//                .orElse(baseDate.minusDays(1));
//
//        LocalDateTime startOfLastDay = lastDate.atStartOfDay();
//        LocalDateTime endOfLastDay = lastDate.atTime(23, 59, 59);
//
//        Long consultationCount = dashboardRepository.countWaitingConsultations(request.getCompanyId());
//        Long vacationCount = dashboardRepository.countPendingVacations(request.getCompanyId());
//
//        DashboardDto.CompanyStats lastCompanyStats = getCompanyStats(request, lastDate);
//        List<DashboardDto.DepartmentStats> lastDepartmentStats = getDepartmentStats(request, lastDate, startOfLastDay, endOfLastDay);
//
//        return DashboardDto.DashboardResponse.of(lastDate, lastCompanyStats, lastDepartmentStats, null);
//    }

    @Override
    public DashboardDto.DashboardResponse getYesterdayStats(DashboardDto.DashboardRequest request) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start("yesterday");

        LocalDate baseDate = getBaseDate(request);
        LocalDate lastDate = attendanceRepository
                .findLatestWorkDateBefore(request.getCompanyId(), baseDate)
                .orElse(baseDate.minusDays(1));
        LocalDateTime startOfLastDay = lastDate.atStartOfDay();
        LocalDateTime endOfLastDay = lastDate.atTime(23, 59, 59);

        CompletableFuture<DashboardDto.CompanyStats> companyFuture =
                CompletableFuture.supplyAsync(() -> getCompanyStats(request, lastDate));

        CompletableFuture<List<DashboardDto.DepartmentStats>> departmentFuture =
                CompletableFuture.supplyAsync(() -> getDepartmentStats(request, lastDate, startOfLastDay, endOfLastDay));

        CompletableFuture.allOf(companyFuture, departmentFuture).join();

        stopWatch.stop();
        log.info("StopWatch yesterday: {} seconds\n{}", stopWatch.getTotalTimeSeconds(), stopWatch.prettyPrint());

        return DashboardDto.DashboardResponse.of(
                lastDate,
                companyFuture.join(),
                departmentFuture.join(),
                null);
    }
}