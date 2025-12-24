package com.kt.social.domain.dashboard.service;

import com.kt.social.common.exception.BadRequestException;
import com.kt.social.domain.dashboard.dto.*;
import com.kt.social.domain.dashboard.enums.DashboardStatsType;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.report.enums.ReportStatus;
import com.kt.social.domain.user.repository.UserRepository;
import com.kt.social.domain.post.repository.PostRepository;
import com.kt.social.domain.comment.repository.CommentRepository;
import com.kt.social.domain.report.repository.ReportRepository;
import com.kt.social.domain.moderation.repository.ModerationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ReportRepository reportRepository;
    private final ModerationLogRepository moderationLogRepository;

    @Transactional(readOnly = true)
    public DashboardSummary getSummary() {
        return DashboardSummary.builder()
                .totalUsers(userRepository.count())
                .totalPosts(postRepository.count())
                .totalComments(commentRepository.count())
                .pendingReports(reportRepository.countByStatus(ReportStatus.PENDING))
                .blockedContentCount(moderationLogRepository.count())
                .build();
    }

    @Transactional(readOnly = true)
    public ModerationStats getModerationStats() {
        long autoBan = moderationLogRepository.countAutoBanned();
        long manualBan = moderationLogRepository.countManualBanned();

        List<Object[]> reasons = moderationLogRepository.countByReason();
        List<ChartData> chartData = new ArrayList<>();

        for (Object[] row : reasons) {
            String reason = (String) row[0];
            Long count = (Long) row[1];
            chartData.add(ChartData.builder()
                    .label(reason == null ? "Khác" : reason)
                    .value(count)
                    .build());
        }

        return ModerationStats.builder()
                .totalAutoBanned(autoBan)
                .totalManualBanned(manualBan)
                .violationTypeDistribution(chartData)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ChartData> getNewUserTrend() {
        List<Object[]> data = userRepository.countNewUsersLast7Days();
        List<ChartData> result = new ArrayList<>();

        for (Object[] row : data) {
            result.add(ChartData.builder()
                    .label((String) row[0]) // Date string
                    .value(((Number) row[1]).longValue()) // Count
                    .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<MultiSeriesChartData> getCombinedStats(DashboardStatsRequest request) {
        int year;
        int month = 0;
        int maxUnit;

        // 1. Parse Request
        if (request.getType() == DashboardStatsType.YEAR) {
            year = Integer.parseInt(request.getTime());
            maxUnit = 12; // 12 tháng
        } else {
            String[] parts = request.getTime().split("/");
            if (parts.length != 2) throw new IllegalArgumentException("Định dạng tháng sai. Dùng MM/yyyy");
            month = Integer.parseInt(parts[0]);
            year = Integer.parseInt(parts[1]);
            maxUnit = YearMonth.of(year, month).lengthOfMonth(); // Số ngày trong tháng
        }

        // 2. Query Database
        Map<Integer, Long> dataMap;
        if (request.getTargetType() == TargetType.USER) {
            dataMap = fetchData(userRepository, request.getType(), month, year);
        } else if (request.getTargetType() == TargetType.POST) {
            dataMap = fetchData(postRepository, request.getType(), month, year);
        } else if (request.getTargetType() == TargetType.REPORT) { // Đảm bảo TargetType có enum REPORT
            dataMap = fetchData(reportRepository, request.getType(), month, year);
        } else {
            throw new IllegalArgumentException("TargetType không hợp lệ");
        }

        // 3. Merge và Fill dữ liệu (Điền số 0 vào ngày/tháng thiếu)
        List<MultiSeriesChartData> result = new ArrayList<>();

        for (int i = 1; i <= maxUnit; i++) {
            String label = request.getType() == DashboardStatsType.YEAR
                    ? "Tháng " + i
                    : String.format("%02d/%02d", i, month); // Format đẹp: 01/08, 02/08...

            result.add(MultiSeriesChartData.builder()
                    .label(label)
                    .value(dataMap.getOrDefault(i, 0L)) // Sử dụng .value thay vì .data
                    .build());
        }

        return result;
    }

    // Helper: Hàm generic để gọi repository và chuyển List<Object[]> thành Map<Time, Count>
    private Map<Integer, Long> fetchData(Object repository, DashboardStatsType type, int month, int year) {
        List<Object[]> rawData;

        if (repository instanceof UserRepository repo) {
            rawData = (type == DashboardStatsType.YEAR) ? repo.countByYear(year) : repo.countByMonth(month, year);
        } else if (repository instanceof PostRepository repo) {
            rawData = (type == DashboardStatsType.YEAR) ? repo.countByYear(year) : repo.countByMonth(month, year);
        } else if (repository instanceof ReportRepository repo) {
            rawData = (type == DashboardStatsType.YEAR) ? repo.countByYear(year) : repo.countByMonth(month, year);
        } else {
            return new HashMap<>();
        }

        // Chuyển List<Object[]> [Time, Count] thành Map<Integer, Long> để dễ lookup
        Map<Integer, Long> map = new HashMap<>();
        for (Object[] row : rawData) {
            Integer timeUnit = ((Number) row[0]).intValue();
            Long count = ((Number) row[1]).longValue();
            map.put(timeUnit, count);
        }

        return map;
    }
}