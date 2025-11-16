package com.kt.social.domain.audit.mapper;

import com.kt.social.domain.audit.dto.ActivityLogDto;
import com.kt.social.domain.audit.model.ActivityLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ActivityLogMapper {

    @Mapping(source = "actor.id", target = "actorId")
    ActivityLogDto toDto(ActivityLog activityLog);
}
