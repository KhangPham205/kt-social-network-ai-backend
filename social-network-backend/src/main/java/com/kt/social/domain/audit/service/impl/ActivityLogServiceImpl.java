package com.kt.social.domain.audit.service.impl;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.audit.dto.ActivityLogDto;
import com.kt.social.domain.audit.mapper.ActivityLogMapper;
import com.kt.social.domain.audit.model.ActivityLog;
import com.kt.social.domain.audit.repository.ActivityLogRepository;
import com.kt.social.domain.audit.service.ActivityLogService;
import com.kt.social.domain.user.model.User;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityLogServiceImpl implements ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final ActivityLogMapper activityLogMapper;

    @Override
    @Async("asyncTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logActivity(User actor, String action, String targetResourceType, Long targetResourceId, Map<String, Object> details) {
        try {
            ActivityLog logEntry = ActivityLog.builder()
                    .actor(actor)
                    .action(action)
                    .targetResourceType(targetResourceType)
                    .targetResourceId(targetResourceId)
                    .details(details)
                    .build();

            activityLogRepository.save(logEntry);

        } catch (Exception e) {
            log.error("Lỗi khi ghi Activity Log (bất đồng bộ): {}", e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<ActivityLogDto> getLogsForUser(Long userId, String filter, Pageable pageable) {

        Specification<ActivityLog> finalSpec = (root, query, cb) ->
                cb.equal(root.get("actor").get("id"), userId);

        if (filter != null && !filter.isBlank()) {
            Specification<ActivityLog> filterSpec = RSQLJPASupport.toSpecification(filter);
            finalSpec = finalSpec.and(filterSpec);
        }

        Page<ActivityLog> logPage = activityLogRepository.findAll(finalSpec, pageable);

        List<ActivityLogDto> content = logPage.getContent().stream()
                .map(activityLogMapper::toDto)
                .toList();

        return PageVO.<ActivityLogDto>builder()
                .page(logPage.getNumber())
                .size(logPage.getSize())
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .numberOfElements(content.size())
                .content(content)
                .build();
    }
}
