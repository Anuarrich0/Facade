package matchmaking;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    @TempDir Path disk;

    private String operateMenu(String input) {
        InputStream previousIn = System.in;
        PrintStream previousOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            Main.main(new String[] { disk.toString() });
            return output.toString(StandardCharsets.UTF_8);
        } finally {
            System.setIn(previousIn);
            System.setOut(previousOut);
        }
    }

    @Test void fullMatchRetriesInvalidRating() {
        String output = operateMenu("1\nAnuar\ninvalid\n1500\n2\n50\nno\n4\n\n0\n");
        assertTrue(output.contains("Enter a whole number from 0 to 3000."));
        assertTrue(output.contains("Match (C): confirmed"));
        String id = output.split("Request created. ID: ")[1].split("\\R")[0];
        Workflow saved = new WorkflowRepository(new Store(disk)).find(id);
        assertEquals("Anuar", saved.player());
        assertEquals(Workflow.Status.COMPLETED, saved.status());
    }

    @Test void bothFailureDemosShowActualPersistedCompensation() {
        String output = operateMenu("6\nPlayer\n1500\n2\n7\nPlayer\n1500\n2\n0\n");
        assertTrue(output.contains("Ping exceeds 120 ms"));
        assertTrue(output.contains("Anti-cheat rejected the player"));
        String[] created = output.split("Request created. ID: ");
        Store store = new Store(disk);
        String b = created[1].split("\\R")[0];
        String c = created[2].split("\\R")[0];
        assertEquals("CANCELLED", store.read("party", b).getProperty("status"));
        assertTrue(store.read("server", b).isEmpty());
        assertEquals("RELEASED", store.read("server", c).getProperty("status"));
        assertEquals("CANCELLED", store.read("party", c).getProperty("status"));
        assertTrue(store.read("match", c).isEmpty());
    }

    @Test void stepMenuReusesLastIdAndShowsStateAfterRejection() {
        String output = operateMenu("2\nPlayer\n1500\n2\n50\nyes\n3\n\n3\n\n4\n\n0\n");
        assertTrue(output.contains("Server (B): allocated"));
        assertTrue(output.contains("Server (B): released"));
        assertTrue(output.contains("Request: cancelled"));
    }
}
