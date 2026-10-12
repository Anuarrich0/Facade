package matchmaking.match;

import matchmaking.StageRejected;

public final class MatchService {
    private final MatchRepository repository;
    private final AntiCheatRule rule;
    public MatchService(MatchRepository repository, AntiCheatRule rule) { this.repository = repository; this.rule = rule; }
    public MatchRecord confirm(String id, boolean suspicious) {
        rule.check(suspicious);
        if (repository.find(id) != null) throw new StageRejected("Match record already exists");
        MatchRecord match = new MatchRecord(id, suspicious, MatchRecord.Status.NEW).confirm();
        repository.save(match);
        return match;
    }
    public MatchRecord revoke(String id) {
        MatchRecord match = repository.find(id);
        if (match == null) throw new StageRejected("Match record not found");
        match = match.revoke();
        repository.save(match);
        return match;
    }
    public MatchRecord inspect(String id) { return repository.find(id); }
}
