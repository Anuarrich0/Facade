package matchmaking;

public record Workflow(String id, String player, int rating, int partySize, int ping,
                       boolean suspicious, Status status) {
    public Workflow {
        java.util.Objects.requireNonNull(id, "id");
        java.util.Objects.requireNonNull(status, "status");
    }
    public enum Status { PENDING_B, PENDING_C, COMPLETED, COMPENSATING, COMPENSATED }
    public Workflow withStatus(Status next) {
        return new Workflow(id, player, rating, partySize, ping, suspicious, next);
    }
}
