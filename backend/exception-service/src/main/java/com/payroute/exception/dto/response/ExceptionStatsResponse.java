package com.payroute.exception.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregate stats for the Exception Queue dashboard cards.
 * All counts are global (unfiltered) so the header cards always
 * reflect the full picture regardless of any active table filter.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionStatsResponse {
    private long total;
    private long open;
    private long inProgress;
    private long escalated;
    private long resolved;
    private long closed;
    /** Count of non-terminal cases older than the SLA breach threshold (24 h). */
    private long slaBreached;
}
