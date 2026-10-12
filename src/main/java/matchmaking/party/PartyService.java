package matchmaking.party;

import matchmaking.StageRejected;

public final class PartyService {
    private final PartyRepository repository;
    private final RatingRule rule;
    public PartyService(PartyRepository repository, RatingRule rule) { this.repository = repository; this.rule = rule; }
    public PartyTicket reserve(String id, String player, int rating, int size) {
        if (player == null || player.isBlank()) throw new StageRejected("Player name is required");
        rule.check(rating, size);
        if (repository.find(id) != null) throw new StageRejected("Party already exists");
        PartyTicket ticket = new PartyTicket(id, player, rating, size, PartyTicket.Status.NEW).reserve();
        repository.save(ticket);
        return ticket;
    }
    public PartyTicket cancel(String id) {
        PartyTicket ticket = repository.find(id);
        if (ticket == null) throw new StageRejected("Party not found");
        ticket = ticket.cancel();
        repository.save(ticket);
        return ticket;
    }
    public PartyTicket inspect(String id) { return repository.find(id); }
}
