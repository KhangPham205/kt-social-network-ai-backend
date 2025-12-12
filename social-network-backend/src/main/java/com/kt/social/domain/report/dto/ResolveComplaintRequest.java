package com.kt.social.domain.report.dto;

import com.kt.social.domain.report.enums.ComplaintStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResolveComplaintRequest {
    private ComplaintStatus decision;
    private String adminNote;
}