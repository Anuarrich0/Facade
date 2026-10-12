package matchmaking;

import matchmaking.party.PartyService;
import matchmaking.server.ServerService;
import matchmaking.match.MatchService;

/** Narrow inspection and manual compensation entry point. */
public final class AdminFacade {
    private final MatchmakingFacade workflow;
    private final PartyService parties;
    private final ServerService servers;
    private final MatchService matches;

    public AdminFacade(MatchmakingFacade workflow, PartyService parties, ServerService servers, MatchService matches) {
        this.workflow = workflow;
        this.parties = parties;
        this.servers = servers;
        this.matches = matches;
    }
    public String inspect(String id) {
        Workflow flow = workflow.require(id);
        return "workflow=" + flow.status() + ", party=" + status(parties.inspect(id))
                + ", server=" + status(servers.inspect(id)) + ", match=" + status(matches.inspect(id));
    }
    public Workflow compensate(String id) { return workflow.compensate(id); }
    private String status(Object entity) {
        if (entity == null) return "NONE";
        if (entity instanceof matchmaking.party.PartyTicket p) return p.status().name();
        if (entity instanceof matchmaking.server.ServerAllocation s) return s.status().name();
        return ((matchmaking.match.MatchRecord) entity).status().name();
    }
}
