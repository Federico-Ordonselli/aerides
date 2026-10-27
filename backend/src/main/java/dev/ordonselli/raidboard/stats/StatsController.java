package dev.ordonselli.raidboard.stats;

import java.time.Instant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}/stats")
@Tag(name = "Statistics")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    @PreAuthorize("@groupSecurity.isMember(#groupId)")
    @Operation(summary = "Attendance per member, events per month and most unfilled roles "
            + "(default period: the last 12 months; zone is an IANA id such as Europe/Rome)")
    public GroupStatsResponse get(@PathVariable Long groupId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String zone) {
        return statsService.forGroup(groupId, from, to, zone);
    }
}
