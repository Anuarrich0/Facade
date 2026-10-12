package matchmaking.server;

import matchmaking.StageRejected;

public final class LatencyRule {
    public void check(int ping) {
        if (ping < 0) throw new StageRejected("Ping cannot be negative");
        if (ping > 120) throw new StageRejected("No suitable server: ping above 120 ms");
    }
}
