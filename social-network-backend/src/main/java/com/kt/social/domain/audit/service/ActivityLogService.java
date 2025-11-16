package com.kt.social.domain.audit.service;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.audit.dto.ActivityLogDto;
import com.kt.social.domain.user.model.User;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface ActivityLogService {
    /**
     * Ghi lại một hành động của user (bất đồng bộ)
     * @param actor Người thực hiện
     * @param action Hành động (ví dụ: "POST:CREATE")
     * @param targetResourceType Loại đối tượng (ví dụ: "Post")
     * @param targetResourceId ID của đối tượng
     * @param details Chi tiết (JSONB)
     */
    void logActivity(User actor, String action, String targetResourceType, Long targetResourceId, Map<String, Object> details);

    PageVO<ActivityLogDto> getLogsForUser(Long userId, String filter, Pageable pageable);}
