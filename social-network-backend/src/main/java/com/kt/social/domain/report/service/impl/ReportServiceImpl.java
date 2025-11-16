package com.kt.social.domain.report.service.impl;

import com.kt.social.common.exception.BadRequestException;
import com.kt.social.common.exception.ResourceNotFoundException;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.comment.repository.CommentRepository;
import com.kt.social.domain.post.repository.PostRepository;
import com.kt.social.domain.report.dto.ReportDto;
import com.kt.social.domain.report.dto.ReportRequestDto;
import com.kt.social.domain.report.dto.ReportReviewDto;
import com.kt.social.domain.report.enums.ReportReason;
import com.kt.social.domain.report.enums.ReportStatus;
import com.kt.social.domain.report.mapper.ReportMapper;
import com.kt.social.domain.report.model.Report;
import com.kt.social.domain.report.repository.ReportRepository;
import com.kt.social.domain.report.service.ReportService;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.repository.UserRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ReportMapper reportMapper;


    @Override
    @Transactional
    public ReportDto createReport(Long reporterId, ReportRequestDto request) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new ResourceNotFoundException("Reporter not found"));

        // thêm logic kiểm tra xem Post/Comment có tồn tại không

        // Kiểm tra xem đã report chưa
        boolean alreadyReported = reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                reporterId, request.getTargetType(), request.getTargetId()
        );
        if (alreadyReported) {
            throw new BadRequestException("Bạn đã báo cáo nội dung này rồi.");
        }

        Report report = Report.builder()
                .reporter(reporter)
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .reason(request.getReason())
                .customReason(request.getReason() == ReportReason.OTHER ? request.getCustomReason() : null)
                .status(ReportStatus.PENDING)
                .build();

        Report savedReport = reportRepository.save(report);
        return reportMapper.toDto(savedReport);
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<ReportDto> getReports(String filter, ReportStatus status, Pageable pageable) {
        Specification<Report> finalSpec = (root, query, cb) ->
                cb.equal(root.get("status"), status);

        if (filter != null && !filter.isBlank()) {
            finalSpec = finalSpec.and(RSQLJPASupport.toSpecification(filter));
        }

        Page<Report> reportPage = reportRepository.findAll(finalSpec, pageable);

        List<ReportDto> content = reportPage.getContent().stream()
                .map(reportMapper::toDto)
                .toList();

        return PageVO.<ReportDto>builder()
                .page(reportPage.getNumber())
                .size(reportPage.getSize())
                .totalElements(reportPage.getTotalElements())
                .totalPages(reportPage.getTotalPages())
                .numberOfElements(content.size())
                .content(content)
                .build();
    }

    @Override
    @Transactional
    public ReportDto reviewReport(Long reportId, ReportReviewDto request, Long reviewerId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new ResourceNotFoundException("Reviewer not found"));

        if (report.getStatus() != ReportStatus.PENDING) {
            throw new BadRequestException("Báo cáo này đã được xử lý.");
        }

        report.setReviewer(reviewer);
        report.setStatus(request.getStatus()); // APPROVED hoặc REJECTED
        report.setModeratorNotes(request.getModeratorNotes());
        report.setReviewedAt(Instant.now());

        Report savedReport = reportRepository.save(report);

        // (Tùy chọn: Nếu status == APPROVED, bạn có thể gọi
        // postService.deletePost(report.getTargetId()) ở đây)

        return reportMapper.toDto(savedReport);
    }
}
