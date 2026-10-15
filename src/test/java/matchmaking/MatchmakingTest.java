package matchmaking;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import matchmaking.party.*;
import matchmaking.server.*;
import matchmaking.match.*;
import static org.junit.jupiter.api.Assertions.*;

class MatchmakingTest {
    @TempDir Path disk;

    private Rig gameAt(Path path) { return new Rig(path); } // domain test language: gameAt(clean disk)
    private record Rig(MatchmakingFacade game, AdminFacade admin, PartyService party,
                       ServerService server, MatchService match, Store store) {
        Rig(Path path) {
            this(new Store(path));
        }
        private Rig(Store store) {
            this(new PartyService(new PartyRepository(store), new RatingRule()),
                    new ServerService(new ServerRepository(store), new LatencyRule()),
                    new MatchService(new MatchRepository(store), new AntiCheatRule()),
                    store);
        }
        private Rig(PartyService party, ServerService server, MatchService match, Store store) {
            this(new MatchmakingFacade(party, server, match, store), null, party, server, match, store);
        }
        private Rig {
            if (admin == null) admin = new AdminFacade(game, party, server, match);
        }
    }

    @DisplayName("Матч завершается и сохраняется, если все проверки пройдены")
    @Test void shouldCompleteAndSaveMatchWhenAllChecksPass() {
        Rig rig = gameAt(disk);
        Workflow flow = rig.game.run("Anuar", 1500, 3, 55, false);
        assertEquals(Workflow.Status.COMPLETED, flow.status());
        assertEquals("workflow=COMPLETED, party=RESERVED, server=ALLOCATED, match=CONFIRMED",
                rig.admin.inspect(flow.id()));
        assertEquals(Workflow.Status.COMPLETED, gameAt(disk).game.require(flow.id()).status());
        assertEquals(Workflow.Status.COMPLETED, rig.game.advance(flow.id()).status());
    }

    @DisplayName("Группа отменяется, если пинг превышает 120 мс")
    @Test void shouldCancelPartyWhenPingExceeds120Ms() {
        Rig rig = gameAt(disk);
        Workflow flow = rig.game.begin("Anuar", 1500, 2, 121, false);
        assertThrows(StageRejected.class, () -> rig.game.advance(flow.id()));
        assertEquals("workflow=COMPENSATED, party=CANCELLED, server=NONE, match=NONE",
                gameAt(disk).admin.inspect(flow.id()));
        assertEquals(Workflow.Status.COMPENSATED, rig.game.advance(flow.id()).status());
    }

    @DisplayName("Отказ античита освобождает сервер и отменяет группу")
    @Test void shouldReleaseServerAndCancelPartyWhenAntiCheatRejectsPlayer() {
        Rig rig = gameAt(disk);
        Workflow flow = rig.game.begin("Anuar", 1500, 2, 50, true);
        assertEquals(Workflow.Status.PENDING_C, rig.game.advance(flow.id()).status());
        assertThrows(StageRejected.class, () -> rig.game.advance(flow.id()));
        assertEquals("workflow=COMPENSATED, party=CANCELLED, server=RELEASED, match=NONE",
                gameAt(disk).admin.inspect(flow.id()));
    }

    @DisplayName("После перезапуска подбор матча продолжается с сохранённого шага")
    @Test void shouldContinueMatchFromSavedStepAfterRestart() {
        Workflow flow = gameAt(disk).game.begin("Anuar", 1000, 1, 30, false);
        Rig afterRestart = gameAt(disk);
        assertEquals(Workflow.Status.PENDING_B, afterRestart.game.require(flow.id()).status());
        afterRestart.game.advance(flow.id());
        assertEquals(Workflow.Status.PENDING_C, gameAt(disk).game.require(flow.id()).status());
        assertEquals(Workflow.Status.COMPLETED, gameAt(disk).game.advance(flow.id()).status());
    }

    @DisplayName("Уже сохранённый сервер не выделяется повторно при отставшем статусе запроса")
    @Test void shouldReuseSavedServerWhenWorkflowStillWaitsForServer() {
        Rig rig = gameAt(disk);
        Workflow flow = rig.game.begin("Anuar", 1000, 1, 30, false);
        rig.server.allocate(flow.id(), 30); // crash after B wrote disk, before facade updated progress
        assertEquals(Workflow.Status.PENDING_C, gameAt(disk).game.advance(flow.id()).status());
    }

    @DisplayName("Уже сохранённый матч не подтверждается повторно при отставшем статусе запроса")
    @Test void shouldReuseSavedMatchWhenWorkflowStillWaitsForConfirmation() {
        Rig rig = gameAt(disk);
        Workflow flow = rig.game.begin("Anuar", 1000, 1, 30, false);
        rig.game.advance(flow.id());
        rig.match.confirm(flow.id(), false); // crash after C wrote disk
        assertEquals(Workflow.Status.COMPLETED, gameAt(disk).game.advance(flow.id()).status());
    }

    @DisplayName("Администратор может отменить готовый матч и безопасно повторить отмену")
    @Test void shouldLetAdminCancelCompletedMatchAndRepeatCancellationSafely() {
        Rig rig = gameAt(disk);
        Workflow flow = rig.game.run("Anuar", 1500, 1, 20, false);
        assertEquals(Workflow.Status.COMPENSATED, gameAt(disk).admin.compensate(flow.id()).status());
        assertEquals("workflow=COMPENSATED, party=CANCELLED, server=RELEASED, match=REVOKED",
                rig.admin.inspect(flow.id()));
        assertEquals(Workflow.Status.COMPENSATED, rig.admin.compensate(flow.id()).status());
    }

    @DisplayName("После перезапуска прерванная отмена доводится до конца")
    @Test void shouldFinishInterruptedCancellationAfterRestart() {
        Rig rig = gameAt(disk);
        Workflow flow = rig.game.run("Anuar", 1500, 1, 20, false);
        rig.store.write("workflow", flow.id(), flow.withStatus(Workflow.Status.COMPENSATING));
        rig.match.revoke(flow.id());
        assertEquals(Workflow.Status.COMPENSATED, gameAt(disk).game.advance(flow.id()).status());
        assertEquals(ServerAllocation.Status.RELEASED, rig.server.inspect(flow.id()).status());
    }

    @DisplayName("Некорректное имя, рейтинг или размер группы вызывают отказ")
    @Test void shouldRejectInvalidPlayerNameRatingOrPartySize() {
        Rig rig = gameAt(disk);
        assertThrows(StageRejected.class, () -> rig.game.begin("Anuar", -1, 1, 20, false));
        assertThrows(StageRejected.class, () -> rig.game.begin(" ", 1000, 1, 20, false));
        assertThrows(StageRejected.class, () -> rig.game.begin("Anuar", 1000, 6, 20, false));
        assertThrows(StageRejected.class, () -> rig.game.begin("Anuar", 3001, 1, 20, false));
        assertThrows(StageRejected.class, () -> rig.game.begin(null, 1000, 1, 20, false));
    }

    @DisplayName("Подсистемы работают напрямую и запрещают недопустимые переходы состояний")
    @Test void shouldAllowDirectSubsystemCallsAndRejectInvalidStateChanges() {
        Rig rig = gameAt(disk);
        PartyTicket party = rig.party.reserve("party1", "Anuar", 1500, 2);
        assertEquals(PartyTicket.Status.RESERVED, gameAt(disk).party.inspect("party1").status());
        assertEquals(PartyTicket.Status.CANCELLED, rig.party.cancel("party1").status());
        assertThrows(IllegalStateException.class, () -> rig.party.cancel("party1"));
        assertThrows(IllegalStateException.class, () -> party.reserve());
        assertThrows(StageRejected.class, () -> rig.party.reserve("party1", "Anuar", 1500, 2));

        ServerAllocation server = rig.server.allocate("server1", 50);
        assertEquals(ServerAllocation.Status.ALLOCATED, gameAt(disk).server.inspect("server1").status());
        assertEquals(ServerAllocation.Status.RELEASED, rig.server.release("server1").status());
        assertThrows(IllegalStateException.class, () -> rig.server.release("server1"));
        assertThrows(IllegalStateException.class, () -> server.allocate());
        assertThrows(StageRejected.class, () -> rig.server.allocate("badping", -1));
        assertThrows(StageRejected.class, () -> rig.server.allocate("server1", 50));

        MatchRecord match = rig.match.confirm("match1", false);
        assertEquals(MatchRecord.Status.CONFIRMED, gameAt(disk).match.inspect("match1").status());
        assertEquals(MatchRecord.Status.REVOKED, rig.match.revoke("match1").status());
        assertThrows(IllegalStateException.class, () -> rig.match.revoke("match1"));
        assertThrows(IllegalStateException.class, () -> match.confirm());
        assertThrows(StageRejected.class, () -> rig.match.confirm("badmatch", true));
        assertThrows(StageRejected.class, () -> rig.match.confirm("match1", false));
    }

    @DisplayName("Отсутствующие записи и недопустимые ID вызывают исключения")
    @Test void shouldRejectMissingRecordsAndInvalidIds() {
        Rig rig = gameAt(disk);
        assertThrows(StageRejected.class, () -> rig.game.require("missing"));
        assertThrows(StageRejected.class, () -> rig.party.cancel("missing"));
        assertThrows(StageRejected.class, () -> rig.server.release("missing"));
        assertThrows(StageRejected.class, () -> rig.match.revoke("missing"));
        assertThrows(IllegalArgumentException.class, () -> rig.party.inspect("../escape"));
    }
}
