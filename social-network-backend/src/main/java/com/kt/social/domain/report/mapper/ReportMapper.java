package com.kt.social.domain.report.mapper;

import com.kt.social.domain.report.dto.ComplaintResponse;
import com.kt.social.domain.report.dto.ReportResponse;
import com.kt.social.domain.report.model.Complaint;
import com.kt.social.domain.report.model.Report;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReportMapper {

    // Map Report -> ReportResponse
    @Mapping(source = "reporter.id", target = "reporterId")
    @Mapping(source = "reporter.displayName", target = "reporterName")
    @Mapping(source = "reporter.avatarUrl", target = "reporterAvatar")
    @Mapping(source = "status", target = "status")
    ReportResponse toResponse(Report report);

    // Map Complaint -> ComplaintResponse
    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.displayName", target = "userDisplayName")
    ComplaintResponse toResponse(Complaint complaint);
}