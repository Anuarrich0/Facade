package matchmaking.server;

import java.util.Properties;
import matchmaking.Store;

public final class ServerRepository {
    private final Store store;
    public ServerRepository(Store store) { this.store = store; }
    public ServerAllocation find(String id) {
        Properties p = store.read("server", id);
        if (p.isEmpty()) return null;
        return new ServerAllocation(id, Integer.parseInt(p.getProperty("ping")),
                ServerAllocation.Status.valueOf(p.getProperty("status")));
    }
    public void save(ServerAllocation allocation) {
        Properties p = new Properties();
        p.setProperty("ping", Integer.toString(allocation.ping()));
        p.setProperty("status", allocation.status().name());
        store.write("server", allocation.id(), p);
    }
}
