package com.code808.calmdesk.domain.dashboard.service.admin;

import com.code808.calmdesk.domain.attendance.entity.Attendance;
import com.code808.calmdesk.domain.attendance.repository.AttendanceRepository;
import com.code808.calmdesk.domain.company.entity.Department;
import com.code808.calmdesk.domain.company.repository.DepartmentRepository;
import com.code808.calmdesk.domain.dashboard.dto.admin.DashboardDto;
import com.code808.calmdesk.domain.dashboard.repository.admin.DashboardRepository;
import com.code808.calmdesk.domain.dashboard.repository.admin.cache.DashboardRedisRepository;
import com.code808.calmdesk.domain.dashboard.repository.admin.projection.AttendanceRateProjection;
import com.code808.calmdesk.domain.dashboard.repository.admin.projection.CompanyStatsProjection;
import com.code808.calmdesk.domain.dashboard.repository.admin.projection.DepartmentCooldownProjection;
import com.code808.calmdesk.domain.dashboard.repository.admin.projection.DepartmentStatsProjection;
import com.code808.calmdesk.domain.dashboard.repository.admin.projection.HighRiskMemberProjection;
import com.code808.calmdesk.domain.dashboard.repository.admin.projection.MemberProjection;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {
    private final DashboardRepository dashboardRepository;
    private final AttendanceRepository attendanceRepository;
    private final DashboardRedisRepository dashboardRedisRepository;
    private final DepartmentRepository departmentRepository;

    private static final int TOP_HIGH_RISK_LIMIT = 5;

    private static final List<Attendance.AttendanceStatus> attendanceStatuses = Arrays.asList(Attendance.AttendanceStatus.ATTEND, Attendance.AttendanceStatus.LATE);

    private LocalDate getBaseDate(DashboardDto.DashboardRequest request) {
        return Optional.ofNullable(request.getDate()).or(() -> attendanceRepository.findLatestWorkDate(request.getCompanyId())).orElseGet(LocalDate::now);
    }

    public List<DashboardDto.DepartmentStats> getDepartmentStats(DashboardDto.DashboardRequest request, LocalDate baseDate, LocalDateTime startOfDay, LocalDateTime endOfDay) {

        Long companyId = request.getCompanyId();
        List<Department> departments = departmentRepository.findByCompany_CompanyId(companyId);

        List<DashboardRedisRepository.AvgCount> avgCounts = new ArrayList<>();

        boolean cacheMiss = false;

        for (Department dept : departments) {
            Optional<DashboardRedisRepository.AvgCount> avgCount = dashboardRedisRepository.getDeptAvgCount(dept.getDepartmentId(), baseDate);

            if (avgCount.isEmpty()) {
                cacheMiss = true;
                break;
            }

            avgCounts.add(avgCount.get());
        }
        // Redis cache가 없을 경우
        if (cacheMiss) {
            List<DepartmentStatsProjection> projections = dashboardRepository.findDepartmentStats(companyId, baseDate, startOfDay, endOfDay);
            return projections.stream()
                    .map(DashboardDto.DepartmentStats::of)
                    .toList();
        }

        List<DepartmentCooldownProjection> cooldownRows = dashboardRepository.findCooldownCountsByCompany(companyId, startOfDay, endOfDay);

        Map<Long, Long> cooldownByDept = cooldownRows.stream()
                .collect(Collectors.toMap(DepartmentCooldownProjection::getDepartmentId,
                        DepartmentCooldownProjection::getCooldownCount));

        List<DashboardDto.DepartmentStats> result = new ArrayList<>();

        for (int i = 0; i < departments.size(); i++) {
            Department dept = departments.get(i);
            DashboardRedisRepository.AvgCount avgCount = avgCounts.get(i);
            long cooldownCount = cooldownByDept.getOrDefault(dept.getDepartmentId(), 0L);
            double avg = avgCount.avg();

            result.add(DashboardDto.DepartmentStats.builder()
                    .departmentId(dept.getDepartmentId())
                    .departmentName(dept.getDepartmentName())
                    .avgStressLevel(avg).avgStressPercentage((double) DashboardDto.convertScore(avg))
                    .memberCount(avgCount.count())
                    .cooldownCount(cooldownCount)
                    .build());
        }
        return result;
    }


    public List<DashboardDto.HighRiskMember> getHighRiskMembers(DashboardDto.DashboardRequest request, LocalDate baseDate) {

        Long companyId = request.getCompanyId();
        double threshold = request.getThreshold();

        Optional<Set<ZSetOperations.TypedTuple<String>>> topMembers = dashboardRedisRepository.getTopMembers(companyId, baseDate, threshold, TOP_HIGH_RISK_LIMIT);

        // 예외 예방
        if (topMembers.isEmpty()) {

            Pageable top5 = PageRequest.of(0, TOP_HIGH_RISK_LIMIT);
            List<HighRiskMemberProjection> highRiskMembers = dashboardRepository.findHighRiskMembers(companyId, baseDate, request.getThreshold(), top5);

            return highRiskMembers
                    .stream()
                    .map(DashboardDto.HighRiskMember::of)
                    .toList();
        }

        // 위험군 순위 정보 (Redis 조회 + MySQL 정보)
        Set<ZSetOperations.TypedTuple<String>> tuples = topMembers.get();

        if (tuples.isEmpty()) {
            return List.of();
        }

        List<Long> orderedMemberIds = tuples
                .stream()
                .map(t -> Long.parseLong(t.getValue()))
                .toList();

        Map<Long, Double> scoreByMemberId = tuples
                .stream()
                .collect(Collectors.toMap(t -> Long.parseLong(t.getValue()), ZSetOperations.TypedTuple::getScore));

        List<MemberProjection> details = dashboardRepository.findMemberBasicInfoByIds(orderedMemberIds);

        Map<Long, MemberProjection> detailByMemberId = details.stream()
                .collect(Collectors.toMap(MemberProjection::getMemberId, d -> d));

        List<DashboardDto.HighRiskMember> result = orderedMemberIds.stream().map(memberId -> {
            MemberProjection detail = detailByMemberId.get(memberId);
            double stress = scoreByMemberId.get(memberId);

            return DashboardDto.HighRiskMember
                    .builder()
                    .memberId(memberId)
                    .memberName(detail != null ? detail.getMemberName() : null)
                    .departmentName(detail != null ? detail.getDepartmentName() : null)
                    .stressLevel(stress).stressPercentage((double) DashboardDto.convertScore(stress))
                    .build();
        }).toList();

        return result;
    }


    public DashboardDto.CompanyStats getCompanyStats(DashboardDto.DashboardRequest request, LocalDate baseDate) {
        Long companyId = request.getCompanyId();

        Optional<DashboardRedisRepository.AvgCount> avgCount = dashboardRedisRepository.getCompanyAvgCount(companyId, baseDate);
        Optional<Long> highRiskCount = dashboardRedisRepository.getHighRiskCount(companyId, baseDate, request.getThreshold());

        Double avgStress;
        Long totalMembers;
        Long riskCount;

        if (avgCount.isPresent() && highRiskCount.isPresent()) {
            avgStress = avgCount.get().avg();
            totalMembers = avgCount.get().count();
            riskCount = highRiskCount.get();
        } else {
            CompanyStatsProjection todayStats = dashboardRepository.findCompanyStats(companyId, baseDate, request.getThreshold());
            avgStress = todayStats.getAvgStressLevel();
            totalMembers = todayStats.getTotalMembers();
            riskCount = todayStats.getHighRiskCount();
        }

        AttendanceRateProjection todayAttendance = dashboardRepository.findAttendanceRate(companyId, baseDate, attendanceStatuses);

        Long consultationCount = dashboardRepository.countWaitingConsultations(companyId);
        Long vacationCount = dashboardRepository.countPendingVacations(companyId);

        return DashboardDto.CompanyStats.of(avgStress, totalMembers, riskCount, (todayAttendance != null) ? todayAttendance.getAttendanceRate() : 0.0, consultationCount, vacationCount);
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

//    @Override
//    public DashboardDto.DashboardResponse getYesterdayStats(DashboardDto.DashboardRequest request) {
//
//        LocalDate baseDate = getBaseDate(request);
//        LocalDate lastDate = attendanceRepository.findLatestWorkDateBefore(request.getCompanyId(), baseDate).orElse(baseDate.minusDays(1));
//        LocalDateTime startOfLastDay = lastDate.atStartOfDay();
//        LocalDateTime endOfLastDay = lastDate.atTime(23, 59, 59);
//
//        DashboardDto.CompanyStats companyStats = getCompanyStats(request, lastDate);
//        List<DashboardDto.DepartmentStats> departmentStats = getDepartmentStats(request, lastDate, startOfLastDay, endOfLastDay);
//
//        return DashboardDto.DashboardResponse.of(lastDate, companyStats, departmentStats, null);
//    }
}

