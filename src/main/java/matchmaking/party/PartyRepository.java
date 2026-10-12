package matchmaking.party;

import matchmaking.Store;

public final class PartyRepository {
    private final Store store;
    public PartyRepository(Store store) { this.store = store; }
    public PartyTicket find(String id) {
        return store.read("party", id, PartyTicket.class);
    }
    public void save(PartyTicket ticket) {
        store.write("party", ticket.id(), ticket);
    }
}
