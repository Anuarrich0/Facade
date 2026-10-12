package matchmaking.party;

import matchmaking.StageRejected;

public final class RatingRule {
    public void check(int rating, int size) {
        if (rating < 0 || rating > 3000) throw new StageRejected("Skill rating must be 0..3000");
        if (size < 1 || size > 5) throw new StageRejected("Party size must be 1..5");
    }
}
