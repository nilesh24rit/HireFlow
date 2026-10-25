package com.hireflow.analytics.metrics;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsRepository repository;

    public AnalyticsController(AnalyticsRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/overview")
    @Operation(summary = "Application analytics overview")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public Overview overview(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Period period = Period.of(from, to);
        return build(period, null);
    }

    @GetMapping("/admin/overview")
    @Operation(summary = "Administrator application analytics overview")
    @PreAuthorize("hasRole('ADMIN')")
    public Overview adminOverview(@RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return build(Period.of(from, to), null);
    }

    private Overview build(Period period, UUID jobId) {
        return new Overview(repository.total(period.from(), period.to(), jobId),
                repository.byStatus(period.from(), period.to(), jobId),
                repository.trend(period.from(), period.to(), jobId),
                period.from(), period.to(), Instant.now());
    }

    record Overview(long totalApplications, Map<String, Long> applicationsByStatus,
            List<AnalyticsRepository.TrendPoint> submissionTrend, Instant from, Instant to,
            Instant dataFreshAt) {}

    private record Period(Instant from, Instant to) {
        static Period of(Instant from, Instant to) {
            Instant resolvedTo = to == null ? Instant.now() : to;
            Instant resolvedFrom = from == null ? resolvedTo.minus(30, ChronoUnit.DAYS) : from;
            if (!resolvedFrom.isBefore(resolvedTo)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
            }
            if (resolvedTo.minus(366, ChronoUnit.DAYS).isAfter(resolvedFrom)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reporting range is limited to 366 days");
            }
            return new Period(resolvedFrom, resolvedTo);
        }
    }
}
