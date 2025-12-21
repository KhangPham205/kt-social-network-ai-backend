package com.kt.social.domain.dashboard.service;

import com.kt.social.domain.dashboard.dto.*;
import com.kt.social.domain.report.enums.ReportStatus;
import com.kt.social.domain.user.repository.UserRepository;
import com.kt.social.domain.post.repository.PostRepository;
import com.kt.social.domain.comment.repository.CommentRepository;
import com.kt.social.domain.report.repository.ReportRepository;
import com.kt.social.domain.moderation.repository.ModerationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

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
}