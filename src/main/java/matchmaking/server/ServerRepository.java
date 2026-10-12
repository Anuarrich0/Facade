package matchmaking.server;

import matchmaking.Store;

public final class ServerRepository {
    private final Store store;
    public ServerRepository(Store store) { this.store = store; }
    public ServerAllocation find(String id) {
        return store.read("server", id, ServerAllocation.class);
    }
    public void save(ServerAllocation allocation) {
        store.write("server", allocation.id(), allocation);
    }
}
