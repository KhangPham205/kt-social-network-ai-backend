package com.kt.social.domain.report.service.impl;

import com.kt.social.common.exception.BadRequestException;
import com.kt.social.common.exception.ResourceNotFoundException;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.comment.model.Comment;
import com.kt.social.domain.comment.repository.CommentRepository;
import com.kt.social.domain.moderation.model.ModerationLog;
import com.kt.social.domain.moderation.repository.ModerationLogRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ModerationLogRepository moderationLogRepository;
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
            throw new BadRequestException("You have already reported this content.");
        }

        Long targetOwnerId = null;

        if (request.getTargetType() == TargetType.POST) {
            Post post = postRepository.findById(request.getTargetId())
                    .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
            targetOwnerId = post.getAuthor().getId();

        } else if (request.getTargetType() == TargetType.COMMENT) {
            Comment comment = commentRepository.findById(request.getTargetId())
                    .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
            targetOwnerId = comment.getAuthor().getId();

        } else if (request.getTargetType() == TargetType.USER) {
            // Nếu báo cáo chính user đó
            targetOwnerId = request.getTargetId();
        }
        // else if (MESSAGE) ...

        if (targetOwnerId == null) {
            throw new BadRequestException("Không xác định được chủ sở hữu nội dung");
        }

        Report report = Report.builder()
                .reporter(reporter)
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .targetUserId(targetOwnerId) // 🔥 Lưu ID chủ sở hữu vào
                .reason(request.getReason())
                .status(ReportStatus.PENDING)
                .history(new ArrayList<>())
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

        if (report.getHistory() == null) {
            report.setHistory(new ArrayList<>());
        }

        report.getHistory().add(history);

        // 2. Cập nhật trạng thái
        report.setStatus(request.getStatus());

        // 3. Xử lý xóa nội dung nếu APPROVED
        if (request.getStatus() == ReportStatus.APPROVED) {
            softDeleteContent(report.getTargetType(), report.getTargetId());

            saveModerationLog(
                    admin,
                    report.getTargetType(),
                    report.getTargetId(),
                    "ADMIN_BAN",
                    "Report Approved: " + request.getNote()
            );
        }

        return reportMapper.toResponse(reportRepository.save(report));
    }

    @Override
    public ReportResponse getReportById(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        return reportMapper.toResponse(report);
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

    @Override
    @Transactional(readOnly = true)
    public PageVO<ComplaintResponse> getComplaints(String filter, Pageable pageable) {
        Specification<Complaint> spec = Specification.where(null);

        if (filter != null && !filter.isBlank()) {
            Map<String, String> propertyPathMapper = new HashMap<>();
            propertyPathMapper.put("reportId", "report.id");
            propertyPathMapper.put("userId", "user.id");
            propertyPathMapper.put("username", "user.username");
            propertyPathMapper.put("email", "user.email");

            spec = RSQLJPASupport.toSpecification(filter, propertyPathMapper);
        }

        Page<Complaint> page = complaintRepository.findAll(spec, pageable);

        List<ComplaintResponse> content = page.getContent().stream()
                .map(reportMapper::toResponse)
                .toList();

        return PageVO.<ComplaintResponse>builder()
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
        User currentResolver = userService.getCurrentUser();
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

            saveModerationLog(
                    currentResolver,
                    complaint.getReport().getTargetType(),
                    complaint.getReport().getTargetId(),
                    "ADMIN_RESTORE",
                    "Appeal Approved: " + request.getAdminNote()
            );
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

    private void saveModerationLog(User actor, TargetType targetType, Long targetId, String action, String reason) {
        ModerationLog log = ModerationLog.builder()
                .actor(actor)
                .targetType(targetType)
                .targetId(targetId)
                .action(action)
                .reason(reason)
                .build();
        moderationLogRepository.save(log);
    }
}