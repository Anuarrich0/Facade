package matchmaking.party;

import java.util.Properties;
import matchmaking.Store;

public final class PartyRepository {
    private final Store store;
    public PartyRepository(Store store) { this.store = store; }
    public PartyTicket find(String id) {
        Properties p = store.read("party", id);
        if (p.isEmpty()) return null;
        return new PartyTicket(id, p.getProperty("player"), Integer.parseInt(p.getProperty("rating")),
                Integer.parseInt(p.getProperty("size")), PartyTicket.Status.valueOf(p.getProperty("status")));
    }
    public void save(PartyTicket ticket) {
        Properties p = new Properties();
        p.setProperty("player", ticket.player());
        p.setProperty("rating", Integer.toString(ticket.rating()));
        p.setProperty("size", Integer.toString(ticket.size()));
        p.setProperty("status", ticket.status().name());
        store.write("party", ticket.id(), p);
    }
}
