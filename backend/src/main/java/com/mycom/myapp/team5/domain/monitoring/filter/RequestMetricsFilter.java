package com.mycom.myapp.team5.domain.monitoring.filter;

import com.mycom.myapp.team5.domain.monitoring.metric.HttpMetricsRecorder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 모든 HTTP 요청의 처리 시간/성공 여부를 HttpMetricsRecorder에 적재한다.
 * 대시보드 자신의 폴링 트래픽까지 섞이면 지표가 왜곡되므로 모니터링 조회 경로는 집계에서 뺀다.
 */
@Component
@RequiredArgsConstructor
public class RequestMetricsFilter extends OncePerRequestFilter {

    private static final String EXCLUDED_PATH_PREFIX = "/api/admin/monitoring";

    private final HttpMetricsRecorder httpMetricsRecorder;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (request.getRequestURI().startsWith(EXCLUDED_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationNanos = System.nanoTime() - start;
            boolean success = response.getStatus() < 400;
            httpMetricsRecorder.record(durationNanos, success);
        }
    }
}
