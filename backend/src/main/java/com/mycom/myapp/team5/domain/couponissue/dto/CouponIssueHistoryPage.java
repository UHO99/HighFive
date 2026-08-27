package com.mycom.myapp.team5.domain.couponissue.dto;

import java.util.List;

public record CouponIssueHistoryPage(List<CouponIssueHistoryResponse> items, int page, int totalPages, long totalElements) {

}
