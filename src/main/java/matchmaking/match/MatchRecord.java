package matchmaking.match;

public record MatchRecord(String id, boolean suspicious, Status status) {
    public enum Status { NEW, CONFIRMED, REVOKED }
    public MatchRecord confirm() {
        if (status != Status.NEW) throw new IllegalStateException("Match cannot be confirmed from " + status);
        return new MatchRecord(id, suspicious, Status.CONFIRMED);
    }
    public MatchRecord revoke() {
        if (status != Status.CONFIRMED) throw new IllegalStateException("Match cannot be revoked from " + status);
        return new MatchRecord(id, suspicious, Status.REVOKED);
    }
}
