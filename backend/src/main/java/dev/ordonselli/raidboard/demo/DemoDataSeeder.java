package dev.ordonselli.raidboard.demo;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import dev.ordonselli.raidboard.event.CreateEventRequest;
import dev.ordonselli.raidboard.event.EventDetailResponse;
import dev.ordonselli.raidboard.event.EventService;
import dev.ordonselli.raidboard.event.SlotRequest;
import dev.ordonselli.raidboard.group.CreateGroupRequest;
import dev.ordonselli.raidboard.group.GroupDetailResponse;
import dev.ordonselli.raidboard.group.GroupService;
import dev.ordonselli.raidboard.group.JoinGroupRequest;
import dev.ordonselli.raidboard.group.MembershipRole;
import dev.ordonselli.raidboard.group.MembershipService;
import dev.ordonselli.raidboard.signup.SignupService;
import dev.ordonselli.raidboard.user.User;
import dev.ordonselli.raidboard.user.UserRepository;

/**
 * Fills an empty database with demo data at startup, in the {@code dev} and {@code demo} profiles only.
 * <p>
 * Everything goes through the real services, so the data follows the business rules (waitlist, approvals,
 * audit log) exactly as if people had clicked through the app. The only shortcut is SQL that moves a few
 * events and the memberships into the past, because the services refuse events in the past: that gives the
 * statistics page some completed events to count.
 * <p>
 * It runs in one transaction (all or nothing) and only if the demo users don't exist yet, so restarting the
 * application never duplicates anything.
 */
@Component
@Profile({"dev", "demo"})
public class DemoDataSeeder implements ApplicationRunner {

    public static final String PASSWORD = "raidboard-demo";
    public static final String OWNER_EMAIL = "giulia@example.com";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final GroupService groupService;
    private final MembershipService membershipService;
    private final EventService eventService;
    private final SignupService signupService;
    private final JdbcClient jdbcClient;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public DemoDataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder, GroupService groupService,
            MembershipService membershipService, EventService eventService, SignupService signupService,
            JdbcClient jdbcClient, EntityManager entityManager, PlatformTransactionManager transactionManager,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.groupService = groupService;
        this.membershipService = membershipService;
        this.eventService = eventService;
        this.signupService = signupService;
        this.jdbcClient = jdbcClient;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(OWNER_EMAIL)) {
            log.info("Demo data already present, nothing to seed");
            return;
        }
        transactionTemplate.executeWithoutResult(status -> seed());
        log.info("Demo data created: {}, marco@example.com, luca@example.com / password {}", OWNER_EMAIL, PASSWORD);
    }

    private void seed() {
        Long giulia = createUser(OWNER_EMAIL, "Giulia", "it");
        Long marco = createUser("marco@example.com", "Marco", "it");
        Long luca = createUser("luca@example.com", "Luca", "en");

        GroupDetailResponse group = groupService.create(giulia,
                new CreateGroupRequest("Gilda dei Draghi", "Raid del giovedì e serate di gioco da tavolo."));
        Long groupId = group.id();
        groupService.join(marco, new JoinGroupRequest(group.inviteCode()));
        groupService.join(luca, new JoinGroupRequest(group.inviteCode()));
        membershipService.changeRole(groupId, giulia, marco, MembershipRole.OFFICER);

        seedHistory(groupId, giulia, marco, luca);

        // Full tank slot: Giulia confirmed, Luca waitlisted. Withdraw as Giulia and Luca is promoted live.
        EventDetailResponse raid = published(groupId, giulia, event("Raid: Vault of Glass", "Portate le pozioni.",
                atRome(3, 21), false, slot("Tank", 1), slot("Healer", 2), slot("DPS", 3)));
        signUp(groupId, raid, 0, giulia);
        signUp(groupId, raid, 0, luca);
        signUp(groupId, raid, 1, marco);

        // Needs approval: Luca's request waits for an officer
        EventDetailResponse boardGames = published(groupId, marco, event("Serata giochi da tavolo", null,
                atRome(6, 20), true, slot("Giocatore", 5)));
        signUp(groupId, boardGames, 0, luca);

        // A draft: officers and the owner see it, Luca doesn't
        eventService.create(groupId, marco, event("Raid: Crota's End (bozza)", null, atRome(10, 21), false,
                slot("Tank", 2), slot("DPS", 4)));
    }

    /**
     * Three completed raids in the last months, for the statistics: Giulia attended all of them, Marco two,
     * and Luca signed up once and withdrew. The DPS seats were never all taken, so "DPS" is the most unfilled role.
     */
    private void seedHistory(Long groupId, Long giulia, Long marco, Long luca) {
        List<Integer> daysAgo = List.of(75, 45, 14);
        List<Long> eventIds = daysAgo.stream().map(days -> {
            // Created in the future, as the API requires, and moved into the past below
            EventDetailResponse raid = published(groupId, giulia, event("Raid del giovedì", null, atRome(30, 21),
                    false, slot("Tank", 1), slot("Healer", 1), slot("DPS", 3)));
            signUp(groupId, raid, 0, giulia);
            return raid.id();
        }).toList();
        signUp(groupId, eventService.get(groupId, eventIds.get(0), giulia), 1, marco);
        signUp(groupId, eventService.get(groupId, eventIds.get(1), giulia), 2, marco);
        signUp(groupId, eventService.get(groupId, eventIds.get(2), giulia), 2, luca);
        signupService.withdraw(groupId, eventIds.get(2), luca);

        // The shortcut: write pending changes, move dates with SQL, then forget the stale entities
        entityManager.flush();
        for (int i = 0; i < eventIds.size(); i++) {
            jdbcClient.sql("update events set starts_at = :startsAt where id = :id")
                    .param("startsAt", atRome(-daysAgo.get(i), 21).atOffset(ZoneOffset.UTC))
                    .param("id", eventIds.get(i))
                    .update();
        }
        // Everybody joined before the first raid, otherwise attendance wouldn't count it
        jdbcClient.sql("update memberships set joined_at = :joinedAt where group_id = :groupId")
                .param("joinedAt", atRome(-daysAgo.get(0) - 1, 12).atOffset(ZoneOffset.UTC))
                .param("groupId", groupId)
                .update();
        entityManager.clear();

        eventIds.forEach(eventId -> eventService.complete(groupId, eventId, giulia));
    }

    private Long createUser(String email, String displayName, String locale) {
        return userRepository.save(new User(email, passwordEncoder.encode(PASSWORD), displayName, locale)).getId();
    }

    private EventDetailResponse published(Long groupId, Long officer, CreateEventRequest request) {
        return eventService.publish(groupId, eventService.create(groupId, officer, request).id(), officer);
    }

    private void signUp(Long groupId, EventDetailResponse event, int slotIndex, Long userId) {
        signupService.signUp(groupId, event.id(), userId, event.slots().get(slotIndex).id());
    }

    private static CreateEventRequest event(String title, String description, Instant startsAt,
            boolean requiresApproval, SlotRequest... slots) {
        return new CreateEventRequest(title, description, null, startsAt, 180, requiresApproval, 2, List.of(slots),
                null, null);
    }

    private static SlotRequest slot(String roleName, int capacity) {
        return new SlotRequest(null, roleName, capacity);
    }

    /** {@code days} from today (negative for the past) at {@code hour}:00, Italian time. */
    private Instant atRome(int days, int hour) {
        return LocalDate.now(clock.withZone(ROME)).plusDays(days).atTime(LocalTime.of(hour, 0)).atZone(ROME)
                .toInstant();
    }
}
