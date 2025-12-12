package com.kt.social.domain.report.service.impl;

import com.kt.social.common.exception.BadRequestException;
import com.kt.social.common.exception.ResourceNotFoundException;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.comment.model.Comment;
import com.kt.social.domain.comment.repository.CommentRepository;
import com.kt.social.domain.post.model.Post;
import com.kt.social.domain.post.repository.PostRepository;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.report.dto.*;
import com.kt.social.domain.report.enums.ComplaintStatus;
import com.kt.social.domain.report.enums.ReportReason;
import com.kt.social.domain.report.enums.ReportStatus;
import com.kt.social.domain.report.mapper.ReportMapper;
import com.kt.social.domain.report.model.Complaint;
import com.kt.social.domain.report.model.Report;
import com.kt.social.domain.report.repository.ComplaintRepository;
import com.kt.social.domain.report.repository.ReportRepository;
import com.kt.social.domain.report.service.ReportService;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.repository.UserRepository;
import com.kt.social.domain.user.service.UserService;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserService userService;
    private final ReportMapper reportMapper;

    // --- REPORT LOGIC ---

    @Override
    @Transactional
    public ReportResponse createReport(Long reporterId, CreateReportRequest request) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new ResourceNotFoundException("Reporter not found"));

        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, request.getTargetType(), request.getTargetId())) {
            throw new BadRequestException("Bạn đã báo cáo nội dung này rồi.");
        }

        Report report = Report.builder()
                .reporter(reporter)
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .reason(request.getReason())
                .customReason(request.getReason() == ReportReason.OTHER ? request.getCustomReason() : null)
                .status(ReportStatus.PENDING)
                .history(new ArrayList<>()) // Init list rỗng
                .build();

        return reportMapper.toResponse(reportRepository.save(report));
    }

    @Override
    @Transactional
    public ReportResponse processReport(Long reportId, ProcessReportRequest request) {
        User admin = userService.getCurrentUser();
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        // 1. Thêm lịch sử (JSONB)
        Report.ReportHistory history = Report.ReportHistory.builder()
                .actorId(admin.getId())
                .actorName(admin.getDisplayName())
                .oldStatus(report.getStatus())
                .newStatus(request.getStatus())
                .note(request.getNote())
                .timestamp(Instant.now())
                .build();

        report.getHistory().add(history);

        // 2. Cập nhật trạng thái
        report.setStatus(request.getStatus());

        // 3. Xử lý xóa nội dung nếu APPROVED
        if (request.getStatus() == ReportStatus.APPROVED) {
            softDeleteContent(report.getTargetType(), report.getTargetId());
        }

        return reportMapper.toResponse(reportRepository.save(report));
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<ReportResponse> getReports(String filter, Pageable pageable) {
        Specification<Report> spec = Specification.where(null);
        if (filter != null && !filter.isBlank()) {
            spec = RSQLJPASupport.toSpecification(filter);
        }
        Page<Report> page = reportRepository.findAll(spec, pageable);

        List<ReportResponse> content = page.getContent().stream()
                .map(reportMapper::toResponse)
                .toList();

        return PageVO.<ReportResponse>builder()
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .numberOfElements(content.size())
                .content(content)
                .build();
    }

    // --- COMPLAINT LOGIC ---

    @Override
    @Transactional
    public ComplaintResponse createComplaint(CreateComplaintRequest request) {
        User currentUser = userService.getCurrentUser();
        Report report = reportRepository.findById(request.getReportId())
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        if (report.getStatus() != ReportStatus.APPROVED) {
            throw new BadRequestException("Chỉ có thể khiếu nại báo cáo đã được chấp thuận.");
        }
        if (complaintRepository.existsByReport(report)) {
            throw new BadRequestException("Khiếu nại cho báo cáo này đang chờ xử lý.");
        }

        Complaint complaint = Complaint.builder()
                .report(report)
                .user(currentUser)
                .content(request.getReason())
                .status(ComplaintStatus.PENDING)
                .build();

        return reportMapper.toResponse(complaintRepository.save(complaint));
    }

    @Override
    @Transactional
    public ComplaintResponse resolveComplaint(Long complaintId, ResolveComplaintRequest request) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint not found"));

        complaint.setStatus(request.getDecision());
        complaint.setAdminResponse(request.getAdminNote());

        // Nếu chấp nhận khiếu nại -> Khôi phục nội dung
        if (request.getDecision() == ComplaintStatus.APPROVED_RESTORE) {
            restoreContent(complaint.getReport().getTargetType(), complaint.getReport().getTargetId());

            // Update Report -> REJECTED (để xóa vết vi phạm)
            Report report = complaint.getReport();
            report.setStatus(ReportStatus.REJECTED);
            report.getHistory().add(Report.ReportHistory.builder()
                    .note("System: Tự động cập nhật do khiếu nại thành công")
                    .newStatus(ReportStatus.REJECTED)
                    .timestamp(Instant.now())
                    .build());
            reportRepository.save(report);
        }

        return reportMapper.toResponse(complaintRepository.save(complaint));
    }

    // --- HELPER METHODS ---

    private void softDeleteContent(TargetType type, Long targetId) {
        if (type == TargetType.POST) {
            postRepository.findById(targetId).ifPresent(post -> {
                post.setDeletedAt(Instant.now());
                postRepository.save(post);
            });
        } else if (type == TargetType.COMMENT) {
            commentRepository.findById(targetId).ifPresent(comment -> {
                comment.setDeletedAt(Instant.now());
                commentRepository.save(comment);
            });
        }
    }

    private void restoreContent(TargetType type, Long targetId) {
        if (type == TargetType.POST) {
            postRepository.findById(targetId).ifPresent(post -> {
                post.setDeletedAt(null);
                postRepository.save(post);
            });
        } else if (type == TargetType.COMMENT) {
            commentRepository.findById(targetId).ifPresent(comment -> {
                comment.setDeletedAt(null);
                commentRepository.save(comment);
            });
        }
    }
}