package com.joutio.bff.infrastructure.out.persistence;

import com.joutio.bff.application.ExpoPushStore;
import com.joutio.notification.domain.event.NotificationEvent.NotificationCreatedEvent;
import com.joutio.notification.domain.model.NotificationType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Base PostgreSQL jetable uniquement ; aucun accès à la configuration applicative. */
@EnabledIfEnvironmentVariable(named="JOUTIO_EXPO_TEST_DB_URL", matches=".+")
class ExpoPushJdbcStoreTest {
    static DriverManagerDataSource admin;
    static String schema;
    static JdbcTemplate jdbc;
    static ExpoPushJdbcStore store;
    static TransactionTemplate tx;
    @BeforeAll static void setup() {
        String url=System.getenv("JOUTIO_EXPO_TEST_DB_URL");
        admin=new DriverManagerDataSource(url,"postgres","expo-test-only");
        schema="expo_test_"+UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(admin).execute("CREATE SCHEMA "+schema);
        var source=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,"postgres","expo-test-only");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/bff/V2__expo_push.sql")).execute(source);
        jdbc=new JdbcTemplate(source); store=new ExpoPushJdbcStore(jdbc); tx=new TransactionTemplate(new DataSourceTransactionManager(source));
    }
    @AfterAll static void cleanup() { if (schema!=null) new JdbcTemplate(admin).execute("DROP SCHEMA "+schema+" CASCADE"); }
    @BeforeEach void reset() { jdbc.execute("TRUNCATE expo_push_subscription,expo_push_delivery"); }
    void register(String user,String installation,String token,boolean rematches) {
        tx.executeWithoutResult(status -> store.register(user,installation,token,ExpoPushStore.Platform.ANDROID,rematches));
    }
    NotificationCreatedEvent event(String id,String gameId,Instant createdAt) {
        return new NotificationCreatedEvent(id,"user",NotificationType.CHALLENGE_RECEIVED,"actor","source",null,gameId,Instant.now().plusSeconds(3600),createdAt);
    }
    @Test void replay_and_multiple_devices_create_one_delivery_per_device() {
        register("user","a","token-a",true); register("user","b","token-b",true);
        var e=event("n",null,Instant.now()); store.enqueue(e); store.enqueue(e);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expo_push_delivery",Integer.class)).isEqualTo(2);
    }
    @Test void historical_events_and_disabled_rematches_are_not_enqueued() {
        register("user","a","token-a",false);
        store.enqueue(event("old",null,Instant.now().minusSeconds(60)));
        store.enqueue(event("rematch","game",Instant.now()));
        assertThat(store.claim(Instant.now())).isEmpty();
    }
    @Test void wrong_owner_cannot_unsubscribe_and_reassignment_invalidates_pending_work() {
        register("user","a","token-a",true); store.unregister("other","a");
        store.enqueue(event("n",null,Instant.now()));
        var delivery=store.claim(Instant.now()).orElseThrow(); assertThat(store.eligible(delivery)).isTrue();
        register("other","a","token-a",true); assertThat(store.eligible(delivery)).isFalse();
        register("user","b","token-a",true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expo_push_subscription",Integer.class)).isEqualTo(1);
    }
    @Test void token_rotation_and_preference_changes_cancel_pending_deliveries() {
        register("user","a","token-a",true); store.enqueue(event("n","game",Instant.now()));
        var d=store.claim(Instant.now()).orElseThrow();
        register("user","a","token-a",false); assertThat(store.eligible(d)).isFalse();
        register("user","a","token-b",true); assertThat(store.eligible(d)).isFalse();
    }
    @Test void claims_are_exclusive_across_workers_and_recover_after_lease() throws Exception {
        register("user","a","token-a",true); store.enqueue(event("n",null,Instant.now()));
        try (var workers=Executors.newFixedThreadPool(2)) {
            var tasks=workers.<java.util.Optional<ExpoPushStore.Delivery>>invokeAll(java.util.List.of(() -> store.claim(Instant.now()), () -> store.claim(Instant.now())));
            long claimed=0;
            for (var task: tasks) if (task.get().isPresent()) claimed++;
            assertThat(claimed).isEqualTo(1);
        }
        assertThat(store.claim(Instant.now().plusSeconds(61))).isPresent();
    }
    @Test void tickets_cancellations_and_cleanup_are_persisted() {
        register("user","a","token-a",true); store.enqueue(event("n",null,Instant.now()));
        var d=store.claim(Instant.now()).orElseThrow();
        store.ticket(d.id(),"ticket",Instant.now().minusSeconds(1));
        assertThat(store.claim(Instant.now()).orElseThrow().ticket()).isEqualTo("ticket");
        store.finish(d.id()); assertThat(store.claim(Instant.now().plusSeconds(120))).isEmpty();
        store.enqueue(event("cancel",null,Instant.now())); store.cancel("cancel");
        assertThat(store.claim(Instant.now())).isEmpty();
        store.deleteUser("user");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expo_push_delivery",Integer.class)).isZero();
    }
}
