package matchmaking;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import matchmaking.party.PartyTicket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StoreTest {
    @TempDir Path disk;

    @Test void realJsonPreservesUnicodeQuotesNumbersAndBooleansAfterReopen() throws Exception {
        Workflow flow = new Workflow("game1", "Анвар \"Ace\"\nTeam", 1500, 2, 50, true, Workflow.Status.PENDING_C);
        new Store(disk).write("workflow", flow.id(), flow);
        JsonObject json = JsonParser.parseString(Files.readString(disk.resolve("workflow/game1.json"))).getAsJsonObject();
        assertEquals(flow.player(), json.get("player").getAsString());
        assertTrue(json.getAsJsonPrimitive("rating").isNumber());
        assertTrue(json.getAsJsonPrimitive("suspicious").isBoolean());
        assertEquals(flow, new Store(disk).read("workflow", "game1", Workflow.class));
    }

    @Test void replacingJsonPersistsNewStatusAndLeavesNoTemporaryFiles() throws Exception {
        Store store = new Store(disk);
        PartyTicket party = new PartyTicket("party1", "Player", 1500, 2, PartyTicket.Status.NEW).reserve();
        store.write("party", party.id(), party);
        store.write("party", party.id(), party.cancel());
        assertEquals(PartyTicket.Status.CANCELLED, new Store(disk).read("party", "party1", PartyTicket.class).status());
        try (var files = Files.list(disk.resolve("party"))) { assertEquals(1, files.count()); }
    }

    @Test void corruptedJsonFailsInsteadOfPretendingTheRecordIsMissing() throws Exception {
        Path file = disk.resolve("workflow/game1.json");
        Files.createDirectories(file.getParent());
        Store store = new Store(disk);
        for (String invalid : new String[] { "{broken", "", "null", "{\"id\":\"game1\",\"status\":\"UNKNOWN\"}" }) {
            Files.writeString(file, invalid);
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> store.read("workflow", "game1", Workflow.class));
            assertTrue(error.getMessage().contains("game1.json"));
            assertEquals(invalid, Files.readString(file));
        }
    }
}
