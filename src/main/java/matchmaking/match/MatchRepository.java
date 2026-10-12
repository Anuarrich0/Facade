package matchmaking.match;

import java.util.Properties;
import matchmaking.Store;

public final class MatchRepository {
    private final Store store;
    public MatchRepository(Store store) { this.store = store; }
    public MatchRecord find(String id) {
        Properties p = store.read("match", id);
        if (p.isEmpty()) return null;
        return new MatchRecord(id, Boolean.parseBoolean(p.getProperty("suspicious")),
                MatchRecord.Status.valueOf(p.getProperty("status")));
    }
    public void save(MatchRecord match) {
        Properties p = new Properties();
        p.setProperty("suspicious", Boolean.toString(match.suspicious()));
        p.setProperty("status", match.status().name());
        store.write("match", match.id(), p);
    }
}
