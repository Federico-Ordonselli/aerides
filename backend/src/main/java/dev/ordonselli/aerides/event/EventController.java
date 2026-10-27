package dev.ordonselli.aerides.event;

import java.time.Instant;
import java.util.Set;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.ordonselli.aerides.common.error.BadRequestException;
import dev.ordonselli.aerides.common.security.CurrentUserId;
import dev.ordonselli.aerides.common.web.PageResponse;

@RestController
@RequestMapping("/api/groups/{groupId}/events")
@Tag(name = "Events")
public class EventController {

    /** Sorting on any other property is refused: it would let clients order by fields they can't see. */
    private static final Set<String> SORTABLE_PROPERTIES = Set.of("startsAt", "title", "createdAt");

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    @PreAuthorize("@groupSecurity.isMember(#groupId)")
    @Operation(summary = "List events, paginated and filtered (members don't see drafts)")
    public PageResponse<EventSummaryResponse> list(@PathVariable Long groupId, @CurrentUserId Long userId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) Long gameId,
            @PageableDefault(size = 20, sort = "startsAt") Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new BadRequestException("Events can be sorted only by " + SORTABLE_PROPERTIES);
            }
        }
        return eventService.list(groupId, userId, new EventFilter(from, to, status, gameId), pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@groupSecurity.hasRole(#groupId, 'OFFICER')")
    @Operation(summary = "Create an event as DRAFT, or a weekly series of events (OFFICER or OWNER)")
    public EventDetailResponse create(@PathVariable Long groupId, @CurrentUserId Long userId,
            @Valid @RequestBody CreateEventRequest request) {
        return eventService.create(groupId, userId, request);
    }

    @GetMapping("/{eventId}")
    @PreAuthorize("@groupSecurity.isMember(#groupId)")
    @Operation(summary = "Get an event with its slots and sign-ups")
    public EventDetailResponse get(@PathVariable Long groupId, @PathVariable Long eventId,
            @CurrentUserId Long userId) {
        return eventService.get(groupId, eventId, userId);
    }

    @PutMapping("/{eventId}")
    @PreAuthorize("@groupSecurity.hasRole(#groupId, 'OFFICER')")
    @Operation(summary = "Update one event; 409 if it changed since the given version (OFFICER or OWNER)")
    public EventDetailResponse update(@PathVariable Long groupId, @PathVariable Long eventId,
            @CurrentUserId Long userId, @Valid @RequestBody UpdateEventRequest request) {
        return eventService.update(groupId, eventId, userId, request);
    }

    @PostMapping("/{eventId}/publish")
    @PreAuthorize("@groupSecurity.hasRole(#groupId, 'OFFICER')")
    @Operation(summary = "Publish a draft: members can see it and sign up (OFFICER or OWNER)")
    public EventDetailResponse publish(@PathVariable Long groupId, @PathVariable Long eventId,
            @CurrentUserId Long userId) {
        return eventService.publish(groupId, eventId, userId);
    }

    @PostMapping("/{eventId}/cancel")
    @PreAuthorize("@groupSecurity.hasRole(#groupId, 'OFFICER')")
    @Operation(summary = "Cancel a draft or published event (OFFICER or OWNER)")
    public EventDetailResponse cancel(@PathVariable Long groupId, @PathVariable Long eventId,
            @CurrentUserId Long userId) {
        return eventService.cancel(groupId, eventId, userId);
    }

    @PostMapping("/{eventId}/complete")
    @PreAuthorize("@groupSecurity.hasRole(#groupId, 'OFFICER')")
    @Operation(summary = "Mark a published event as completed, once it has started (OFFICER or OWNER)")
    public EventDetailResponse complete(@PathVariable Long groupId, @PathVariable Long eventId,
            @CurrentUserId Long userId) {
        return eventService.complete(groupId, eventId, userId);
    }
}
