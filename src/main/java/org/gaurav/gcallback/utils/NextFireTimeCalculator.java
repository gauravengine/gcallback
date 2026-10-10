package org.gaurav.gcallback.utils;

import lombok.extern.log4j.Log4j2;
import org.gaurav.gcallback.dao.models.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
@Log4j2
public class NextFireTimeCalculator {

    @Autowired
    private Clock clock;

    public Instant nextAfter(Job job, Instant anchor) {
        return switch (job.getScheduleType()) {
            case ONE_OFF -> null;
            case CRON -> nextCron(job.getCronExpression(), job.getTimeZone(), anchor);
        };
    }

    public Instant firstFireTime(final Job job) {
        return switch (job.getScheduleType()) {
            case ONE_OFF -> job.getRunAt();
            case  CRON -> nextCron(job.getCronExpression(), job.getTimeZone(),
                    clock.instant());
        };
    }
    // returns next cron
    private Instant nextCron(String expression, String timeZone, Instant anchor) {
        CronExpression cron = CronExpression.parse(expression);
        ZonedDateTime from = anchor.atZone(ZoneId.of(timeZone));
        ZonedDateTime next = cron.next(from);

        return next == null ? null : next.toInstant();

    }
}
