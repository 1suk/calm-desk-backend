# 대시보드: emotion_checkin 직접 집계 방식 (비교용)

> 메인은 **집계 테이블 방식**으로 진행. 이 문서는 비교·검증용 대안이다.
> 전제: Redis 집계 제거 + 읽기 DB 단일화(step 1~3)가 이미 적용된 상태 위에서 본다.

## 아이디어
`stress_summary` 롤업을 거치지 않고, 대시보드를 조회 시점에 `emotion_checkin`에서 집계한다.
멤버 단위까지만 DB에서 `GROUP BY` → 회사/부서/고위험군은 앱에서 롤업.
→ FROM 서브쿼리·네이티브 쿼리 불필요(멤버 단위 GROUP BY는 평범한 JPQL).

## 쿼리 (1개로 스트레스 집계 3개 대체)
```jpql
SELECT a.member.memberId, a.member.department.departmentId,
       a.member.department.departmentName, a.member.name,
       AVG(e.stressLevel)
FROM EmotionCheckin e JOIN e.attendance a
WHERE a.member.company.companyId = :companyId AND a.workDate = :date
GROUP BY a.member.memberId, a.member.department.departmentId,
         a.member.department.departmentName, a.member.name
```
결과 = 회사 멤버 1명당 1행(그날 평균 + 소속 부서 + 이름).

## 앱 롤업 (위 결과 하나로 전부 계산)
- 회사 평균 = 멤버 평균들의 평균
- 고위험군 수 = `avg >= threshold`인 행 수
- 멤버 수 = 행 수
- 부서 통계 = `groupingBy(departmentId)` 후 부서별 평균
- Top5 = 정렬 후 상위 5(이름·부서는 행에 이미 있음)

## 바꿀 코드
- `EmotionCheckinRepository`: 위 쿼리 + projection 1개 추가
- `DashboardServiceImpl`: 스트레스 집계 3개(`findCompanyStats`/`findDepartmentStats`/`findHighRiskMembers`) → 쿼리 1 + Java 집계로 교체
- 그대로: 출석률·상담·휴가 쿼리, DTO, SSE

## 집계 의미 주의
멤버 단위 평균을 먼저 낸다(1인 1표). `emotion_checkin`을 바로 `AVG`하면 체크인 많은 멤버가 과대반영 → 현재 `stress_summary` 방식과 숫자가 달라짐. 반드시 멤버로 먼저 GROUP BY.

## 트레이드오프 vs 집계 테이블

| | 직접 집계(이 방식) | 집계 테이블(메인) |
|---|---|---|
| 집계 시점 | 조회 때 매번 | 체크인 때 미리 |
| 정합성 | 항상 원본과 일치 | 증분 유지 필요(어긋남 위험) |
| 쓰기 경로 | 변화 없음 | 테이블 유지 + backfill 필요 |
| 읽기 | 멤버 수만큼 행 전송 → 대규모 불리 | 요약 행만 읽음 → 대규모 유리 |
| 코드량 | 쿼리 1 + 앱 집계(적음) | 테이블 + 쓰기 경로 + 배치(많음) |

## 검증 포인트
1. 결과가 현재 `stress_summary` 방식과 동일한지 (같은 날짜·회사로 대조)
2. 멤버 수 N을 키웠을 때 읽기 시간 추이 (직접 집계는 N에 비례, 집계 테이블은 거의 일정)
