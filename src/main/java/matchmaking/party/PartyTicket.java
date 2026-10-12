package matchmaking.party;

public record PartyTicket(String id, String player, int rating, int size, Status status) {
    public PartyTicket {
        java.util.Objects.requireNonNull(id, "id");
        java.util.Objects.requireNonNull(status, "status");
    }
    public enum Status { NEW, RESERVED, CANCELLED }
    public PartyTicket reserve() {
        if (status != Status.NEW) throw new IllegalStateException("Party cannot be reserved from " + status);
        return new PartyTicket(id, player, rating, size, Status.RESERVED);
    }
    public PartyTicket cancel() {
        if (status != Status.RESERVED) throw new IllegalStateException("Party cannot be cancelled from " + status);
        return new PartyTicket(id, player, rating, size, Status.CANCELLED);
    }
}
