package dev.ordonselli.aerides.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import dev.ordonselli.aerides.event.EventService;
import dev.ordonselli.aerides.group.GroupService;
import dev.ordonselli.aerides.group.MembershipService;
import dev.ordonselli.aerides.signup.SignupService;
import dev.ordonselli.aerides.support.AbstractIntegrationTest;
import dev.ordonselli.aerides.user.UserRepository;

/**
 * The seeder through the real services and PostgreSQL. It only exists in the dev and demo profiles, so
 * here it is built by hand from the beans of the test context.
 * <p>
 * The demo profile context ({@link DemoProfileSecurityIT}) may share this database and seed it first:
 * the assertions hold either way, which is also what "running it again changes nothing" means.
 */
class DemoDataSeederIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private GroupService groupService;
    @Autowired
    private MembershipService membershipService;
    @Autowired
    private EventService eventService;
    @Autowired
    private SignupService signupService;
    @Autowired
    private JdbcClient jdbcClient;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private Clock clock;

    @Test
    void seedsTheDemoGroupOnceThroughTheServices() {
        DemoDataSeeder seeder = new DemoDataSeeder(userRepository, passwordEncoder, groupService, membershipService,
                eventService, signupService, jdbcClient, entityManager, transactionManager, clock);

        seeder.run(null);
        seeder.run(null);

        Long groupId = jdbcClient.sql("select id from groups where name = 'Gilda dei Draghi'")
                .query(Long.class)
                .single(); // exactly one group: the second run did nothing
        assertThat(jdbcClient.sql("select role from memberships where group_id = :g order by role")
                .param("g", groupId).query(String.class).list())
                .containsExactly("MEMBER", "MEMBER", "OFFICER", "OWNER");
        List<Map<String, Object>> statuses = jdbcClient.sql("""
                        select status, count(*) as total from events where group_id = :g group by status order by status
                        """)
                .param("g", groupId).query().listOfRows();
        assertThat(statuses).extracting(row -> row.get("status") + "=" + row.get("total"))
                .containsExactly("COMPLETED=3", "DRAFT=1", "PUBLISHED=2");
        // The business rules ran: Luca is on the waitlist of the full tank slot and pending on the board games
        assertThat(jdbcClient.sql("""
                        select s.status from signups s join users u on u.id = s.user_id join events e on e.id = s.event_id
                        where u.email = 'luca@example.com' and e.status = 'PUBLISHED' order by e.starts_at
                        """).query(String.class).list())
                .containsExactly("WAITLISTED", "PENDING");
        // Every seat of the raid is taken, and one more person waits
        assertThat(jdbcClient.sql("""
                        select s.status, count(*) from signups s join events e on e.id = s.event_id
                        where e.title = 'Raid: Vault of Glass' group by s.status order by s.status
                        """).query((rs, row) -> rs.getString(1) + "=" + rs.getLong(2)).list())
                .containsExactly("CONFIRMED=3", "WAITLISTED=1");
        assertThat(jdbcClient.sql("""
                        select sum(es.capacity) from event_slots es join events e on e.id = es.event_id
                        where e.title = 'Raid: Vault of Glass'
                        """).query(Long.class).single()).isEqualTo(3);
        // Paolo belongs to no group; Elena alone owns another group
        assertThat(jdbcClient.sql("""
                        select count(*) from memberships m join users u on u.id = m.user_id where u.email = 'paolo@example.com'
                        """).query(Long.class).single()).isZero();
        assertThat(jdbcClient.sql("""
                        select u.email || ':' || m.role from memberships m join users u on u.id = m.user_id
                        join groups g on g.id = m.group_id where g.name = 'Night Owls'
                        """).query(String.class).list())
                .containsExactly("elena@example.com:OWNER");
        assertThat(jdbcClient.sql("select locale from users where email in "
                        + "('giulia@example.com','marco@example.com','luca@example.com','sara@example.com',"
                        + "'paolo@example.com','elena@example.com') group by locale order by locale")
                .query(String.class).list())
                .containsExactly("en", "it");
        // ...and so did the audit log
        assertThat(jdbcClient.sql("select count(*) from audit_entries where group_id = :g")
                .param("g", groupId).query(Long.class).single()).isGreaterThan(10);
    }
}
