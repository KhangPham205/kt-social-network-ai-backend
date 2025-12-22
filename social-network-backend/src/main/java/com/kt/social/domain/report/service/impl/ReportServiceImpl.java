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
            Post post = postRepository.findById(Long.valueOf(request.getTargetId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
            targetOwnerId = post.getAuthor().getId();

        } else if (request.getTargetType() == TargetType.COMMENT) {
            Comment comment = commentRepository.findById(Long.valueOf(request.getTargetId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
            targetOwnerId = comment.getAuthor().getId();

        } else if (request.getTargetType() == TargetType.USER) {
            targetOwnerId = Long.valueOf(request.getTargetId());
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
                .customReason(request.getReason().name().equals("OTHER") ? request.getCustomReason() : null) // Nếu reason là OTHER
                .build();

        return reportMapper.toResponse(reportRepository.save(report));
    }

    @Override
    public List<ReportResponse> updateReport(UpdateReportRequest request) {
        List<Report> reports = reportRepository.findAllById(request.getReportIds());

        if (reports.isEmpty()) {
            throw new ResourceNotFoundException("No reports found for the provided IDs");
        }

        if (request.getReportStatus() == null) {
            throw new BadRequestException("Report status must be provided for update");
        }

        for (Report report : reports) {
            report.setStatus(request.getReportStatus());
        }

        List<Report> updatedReports = reportRepository.saveAll(reports);

        return updatedReports.stream()
                .map(reportMapper::toResponse)
                .toList();
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
    @Transactional(readOnly = true)
    public PageVO<ReportResponse> getReportsByContent(String targetId, TargetType targetType, Pageable pageable) {
        Page<Report> page = reportRepository.findByTargetTypeAndTargetId(targetType, targetId, pageable);

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
    public PageVO<ComplaintResponse> getComplaintsByContent(String targetId, TargetType targetType, Pageable pageable) {
        Page<Complaint> page = complaintRepository.findByTargetTypeAndTargetId(targetType, targetId, pageable);

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
    @Transactional
    public ComplaintResponse createComplaint(CreateComplaintRequest request) {
        User currentUser = userService.getCurrentUser();

        // 1. Kiểm tra trùng lặp (Một nội dung chỉ được khiếu nại 1 lần đang chờ xử lý)
        if (complaintRepository.existsByTargetTypeAndTargetId(request.getTargetType(), request.getTargetId())) {
            throw new BadRequestException("Nội dung này đang có khiếu nại chờ xử lý hoặc đã được giải quyết.");
        }

        // 2. Validate và Kiểm tra quyền sở hữu (User chỉ được khiếu nại bài của chính mình)
        validateContentOwnership(currentUser.getId(), request.getTargetType(), Long.valueOf(request.getTargetId()));

        // 3. Tạo Complaint
        Complaint complaint = Complaint.builder()
                .user(currentUser)
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .content(request.getReason())
                .build();

        return reportMapper.toResponse(complaintRepository.save(complaint));
    }

    @Override
    public ComplaintResponse updateComplaint(Long id, ComplaintStatus status) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint not found"));

        if (complaint.getStatus() == null || complaint.getStatus() != ComplaintStatus.PENDING) {
            throw new BadRequestException("Only PENDING complaints can be updated.");
        }

        complaint.setStatus(status);

        return reportMapper.toResponse(complaintRepository.save(complaint));
    }

    // Hàm phụ trợ để kiểm tra nội dung có tồn tại và thuộc về user không
    private void validateContentOwnership(Long userId, TargetType type, Long targetId) {
        if (type == TargetType.POST) {
            // Dùng hàm tìm cả bài đã xóa (vì bài bị xóa mới đi khiếu nại)
            Post post = postRepository.findByIdIncludingDeleted(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bài viết không tồn tại."));

            if (!post.getAuthor().getId().equals(userId)) {
                throw new BadRequestException("Bạn chỉ có thể khiếu nại cho bài viết của chính mình.");
            }

        } else if (type == TargetType.COMMENT) {
            Comment comment = commentRepository.findByIdIncludingDeleted(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bình luận không tồn tại."));

            if (!comment.getAuthor().getId().equals(userId)) {
                throw new BadRequestException("Bạn chỉ có thể khiếu nại cho bình luận của chính mình.");
            }
        } else {
            // Handle Message or User types if needed
            throw new BadRequestException("Loại nội dung không hỗ trợ khiếu nại.");
        }
    }
}