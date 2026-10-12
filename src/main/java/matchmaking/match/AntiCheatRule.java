package matchmaking.match;

import matchmaking.StageRejected;

public final class AntiCheatRule {
    public void check(boolean suspicious) {
        if (suspicious) throw new StageRejected("Anti-cheat rejected a suspicious player");
    }
}
