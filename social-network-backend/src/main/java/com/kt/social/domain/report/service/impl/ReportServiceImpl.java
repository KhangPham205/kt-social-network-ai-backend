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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    // Đã xóa moderationLogRepository (vì logic log thuộc về ModerationService)
    private final ReportRepository reportRepository;
    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserService userService;
    private final ReportMapper reportMapper;

    @Override
    @Transactional
    public ReportResponse createReport(Long reporterId, CreateReportRequest request) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new ResourceNotFoundException("Reporter not found"));

        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, request.getTargetType(), request.getTargetId())) {
            throw new BadRequestException("Bạn đã báo cáo nội dung này rồi.");
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
            targetOwnerId = request.getTargetId();
        }

        if (targetOwnerId == null) {
            throw new BadRequestException("Không xác định được chủ sở hữu nội dung");
        }

        Report report = Report.builder()
                .reporter(reporter)
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .targetUserId(targetOwnerId)
                .reason(request.getReason())
                .customReason(request.getReason().name().equals("OTHER") ? "Người dùng báo cáo khác" : null) // Logic nhỏ nếu cần
                .build();

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
            Map<String, String> propertyPathMapper = new HashMap<>();
            propertyPathMapper.put("id", "id");
            propertyPathMapper.put("targetType", "targetType"); // filter=targetType=='POST'
            propertyPathMapper.put("reason", "reason");
            propertyPathMapper.put("reporter", "reporter.username"); // filter=reporter=='nguyenvana'
            propertyPathMapper.put("targetUserId", "targetUserId");  // filter=targetUserId==10 (Xem ai bị report nhiều)

            spec = RSQLJPASupport.toSpecification(filter, propertyPathMapper);
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
    @Transactional(readOnly = true)
    public PageVO<ComplaintResponse> getComplaints(String filter, Pageable pageable) {
        Specification<Complaint> spec = Specification.where(null);

        if (filter != null && !filter.isBlank()) {
            Map<String, String> propertyPathMapper = new HashMap<>();
            propertyPathMapper.put("reportId", "report.id");
            propertyPathMapper.put("userId", "user.id");
            propertyPathMapper.put("username", "user.username");
            propertyPathMapper.put("email", "user.email");
            propertyPathMapper.put("createdAt", "createdAt");

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

    @Override
    public ComplaintResponse getComplaintById(Long id) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint not found"));
        return reportMapper.toResponse(complaint);
    }

    @Override
    @Transactional
    public ComplaintResponse createComplaint(CreateComplaintRequest request) {
        User currentUser = userService.getCurrentUser();
        Report report = reportRepository.findById(request.getReportId())
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        if (complaintRepository.existsByReport(report)) {
            throw new BadRequestException("Đã tồn tại khiếu nại cho báo cáo này.");
        }

        Complaint complaint = Complaint.builder()
                .report(report)
                .user(currentUser)
                .content(request.getReason())
                .build();

        return reportMapper.toResponse(complaintRepository.save(complaint));
    }
}