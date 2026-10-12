package matchmaking.server;

import matchmaking.StageRejected;

public final class ServerService {
    private final ServerRepository repository;
    private final LatencyRule rule;
    public ServerService(ServerRepository repository, LatencyRule rule) { this.repository = repository; this.rule = rule; }
    public ServerAllocation allocate(String id, int ping) {
        rule.check(ping);
        if (repository.find(id) != null) throw new StageRejected("Server allocation already exists");
        ServerAllocation allocation = new ServerAllocation(id, ping, ServerAllocation.Status.NEW).allocate();
        repository.save(allocation);
        return allocation;
    }
    public ServerAllocation release(String id) {
        ServerAllocation allocation = repository.find(id);
        if (allocation == null) throw new StageRejected("Server allocation not found");
        allocation = allocation.release();
        repository.save(allocation);
        return allocation;
    }
    public ServerAllocation inspect(String id) { return repository.find(id); }
}
