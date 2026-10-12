package matchmaking.match;

import matchmaking.Store;

public final class MatchRepository {
    private final Store store;
    public MatchRepository(Store store) { this.store = store; }
    public MatchRecord find(String id) {
        return store.read("match", id, MatchRecord.class);
    }
    public void save(MatchRecord match) {
        store.write("match", match.id(), match);
    }
}
