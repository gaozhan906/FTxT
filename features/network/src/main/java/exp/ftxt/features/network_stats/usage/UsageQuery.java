package exp.ftxt.features.network_stats.usage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class UsageQuery {
    public final UsagePeriod period;
    public final UsageNetwork network;
    public final ZoneId zone;
    public final long nowMs;

    public UsageQuery(UsagePeriod period, UsageNetwork network) {
        this(period, network, ZoneId.systemDefault(), System.currentTimeMillis());
    }

    public UsageQuery(UsagePeriod period, UsageNetwork network, ZoneId zone, long nowMs) {
        this.period = period != null ? period : UsagePeriod.TODAY;
        this.network = network != null ? network : UsageNetwork.ALL;
        this.zone = zone != null ? zone : ZoneId.systemDefault();
        this.nowMs = nowMs > 0 ? nowMs : System.currentTimeMillis();
    }

    public LocalDate today() {
        return Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate();
    }

    public LocalDate startDate() {
        return today().minusDays(period.days - 1L);
    }

    public long startMs() {
        return startDate().atStartOfDay(zone).toInstant().toEpochMilli();
    }

    public long endMs() {
        return nowMs;
    }

    public String cacheKey() {
        return period.name() + '|' + network.name() + '|' + zone.getId() + '|'
                + today() + '|' + (nowMs / 30000L);
    }
}
