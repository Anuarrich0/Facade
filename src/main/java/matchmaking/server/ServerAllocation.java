package matchmaking.server;

public record ServerAllocation(String id, int ping, Status status) {
    public enum Status { NEW, ALLOCATED, RELEASED }
    public ServerAllocation allocate() {
        if (status != Status.NEW) throw new IllegalStateException("Server cannot be allocated from " + status);
        return new ServerAllocation(id, ping, Status.ALLOCATED);
    }
    public ServerAllocation release() {
        if (status != Status.ALLOCATED) throw new IllegalStateException("Server cannot be released from " + status);
        return new ServerAllocation(id, ping, Status.RELEASED);
    }
}
