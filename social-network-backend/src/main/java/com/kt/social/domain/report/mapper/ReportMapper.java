package com.kt.social.domain.report.mapper;

import com.kt.social.domain.report.dto.ReportDto;
import com.kt.social.domain.report.model.Report;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ReportMapper {

    @Mapping(source = "reporter.id", target = "reporterId")
    @Mapping(source = "reporter.displayName", target = "reporterName")
    @Mapping(source = "reviewer.id", target = "reviewerId")
    ReportDto toDto(Report report);
}
