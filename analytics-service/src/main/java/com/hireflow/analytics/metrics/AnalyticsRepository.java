package com.hireflow.analytics.metrics;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalyticsRepository {
    private final JdbcTemplate jdbc;

    public AnalyticsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long total(Instant from, Instant to, UUID jobId) {
        String sql = "select count(*) from analytics_application_projection where submitted_at >= ? and submitted_at < ?";
        if (jobId != null) sql += " and job_id = ?";
        return jobId == null ? jdbc.queryForObject(sql, Long.class, from, to)
                : jdbc.queryForObject(sql, Long.class, from, to, jobId);
    }

    public Map<String, Long> byStatus(Instant from, Instant to, UUID jobId) {
        String sql = "select current_status, count(*) from analytics_application_projection where submitted_at >= ? and submitted_at < ?";
        if (jobId != null) sql += " and job_id = ?";
        sql += " group by current_status order by current_status";
        List<Object> args = jobId == null ? List.of(from, to) : List.of(from, to, jobId);
        return jdbc.query(sql, args.toArray(), (rs, row) -> Map.entry(
                rs.getString(1), rs.getLong(2))).stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public List<TrendPoint> trend(Instant from, Instant to, UUID jobId) {
        String sql = "select (submitted_at at time zone 'UTC')::date, count(*) from analytics_application_projection where submitted_at >= ? and submitted_at < ?";
        if (jobId != null) sql += " and job_id = ?";
        sql += " group by 1 order by 1";
        List<Object> args = jobId == null ? List.of(from, to) : List.of(from, to, jobId);
        return jdbc.query(sql, args.toArray(), (rs, row) -> new TrendPoint(
                rs.getDate(1).toLocalDate(), rs.getLong(2)));
    }

    public record TrendPoint(java.time.LocalDate date, long count) {}
}
