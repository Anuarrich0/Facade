package matchmaking;

import java.util.UUID;
import matchmaking.party.PartyService;
import matchmaking.party.PartyTicket;
import matchmaking.server.ServerService;
import matchmaking.server.ServerAllocation;
import matchmaking.match.MatchService;
import matchmaking.match.MatchRecord;

/** Coordinates the three independent subsystems using persisted facts. */
public final class MatchmakingFacade {
    private final PartyService parties;
    private final ServerService servers;
    private final MatchService matches;
    private final Store store;

    public MatchmakingFacade(PartyService parties, ServerService servers, MatchService matches, Store store) {
        this.parties = parties;
        this.servers = servers;
        this.matches = matches;
        this.store = store;
    }

    public Workflow begin(String player, int rating, int partySize, int ping, boolean suspicious) {
        if (player.length() > 30)
            throw new StageRejected("Player name must not exceed 30 characters");
        if (player == null || player.isBlank()) throw new StageRejected("Player name is required");
        String id = UUID.randomUUID().toString();
        Workflow flow = new Workflow(id, player, rating, partySize, ping, suspicious, Workflow.Status.PENDING_B);
        store.write("workflow", flow.id(), flow);
        try {
            parties.reserve(id, player, rating, partySize);
        } catch (StageRejected e) {
            store.write("workflow", flow.id(), flow.withStatus(Workflow.Status.COMPENSATED));
            throw e;
        }
        return flow;
    }
    
    public Workflow run(String player, int rating, int partySize, int ping, boolean suspicious) {
        Workflow flow = begin(player, rating, partySize, ping, suspicious);
        return advance(advance(flow.id()).id());
    }

    /** Advances one stage so a live demo can inspect or restart between commits. */
    public Workflow advance(String id) {
        Workflow flow = require(id);
        if (flow.status() == Workflow.Status.COMPENSATING) return compensate(id);
        if (flow.status() == Workflow.Status.COMPLETED || flow.status() == Workflow.Status.COMPENSATED) return flow;
        if (flow.status() == Workflow.Status.PENDING_B) {
            if (parties.inspect(id) == null) parties.reserve(id, flow.player(), flow.rating(), flow.partySize());
            try {
                if (servers.inspect(id) == null) servers.allocate(id, flow.ping());
                flow = flow.withStatus(Workflow.Status.PENDING_C);
                store.write("workflow", flow.id(), flow);
                return flow;
            } catch (StageRejected e) {
                compensate(id);
                throw e;
            }
        }
        try {
            if (matches.inspect(id) == null) matches.confirm(id, flow.suspicious());
            flow = flow.withStatus(Workflow.Status.COMPLETED);
            store.write("workflow", flow.id(), flow);
            return flow;
        } catch (StageRejected e) {
            compensate(id);
            throw e;
        }
    }

    /** Uses the saved entities, including after a restart or partial compensation. */
    public Workflow compensate(String id) {
        Workflow flow = require(id);
        if (flow.status() == Workflow.Status.COMPENSATED) return flow;
        store.write("workflow", flow.id(), flow.withStatus(Workflow.Status.COMPENSATING));
        MatchRecord match = matches.inspect(id);
        if (match != null && match.status() == MatchRecord.Status.CONFIRMED) matches.revoke(id);
        ServerAllocation server = servers.inspect(id);
        if (server != null && server.status() == ServerAllocation.Status.ALLOCATED) servers.release(id);
        PartyTicket party = parties.inspect(id);
        if (party != null && party.status() == PartyTicket.Status.RESERVED) parties.cancel(id);
        flow = flow.withStatus(Workflow.Status.COMPENSATED);
        store.write("workflow", flow.id(), flow);
        return flow;
    }

    public Workflow require(String id) {
        Workflow flow = store.read("workflow", id, Workflow.class);
        if (flow == null) throw new StageRejected("Workflow not found: " + id);
        return flow;
    }
}
