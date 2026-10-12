package matchmaking;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;

/** One entity per file; an atomic rename makes each committed state durable. */
public final class Store {
    private final Path root;
    private final Gson json = new GsonBuilder().setPrettyPrinting().setStrictness(Strictness.STRICT).create();

    public Store(Path root) { this.root = root; }

    public <T> T read(String kind, String id, Class<T> type) {
        Path file = file(kind, id);
        if (!Files.exists(file)) return null;
        try {
            T value = json.fromJson(Files.readString(file), type);
            if (value == null) throw new IllegalStateException("JSON record is empty");
            return value;
        } catch (IOException | RuntimeException e) { throw new IllegalStateException("Cannot read " + file, e); }
    }

    public void write(String kind, String id, Object value) {
        Path file = file(kind, id);
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), "record-", ".tmp");
            try {
                Files.writeString(temp, json.toJson(value));
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temp); }
        } catch (IOException e) { throw new IllegalStateException("Cannot write " + file, e); }
    }

    private Path file(String kind, String id) {
        if (!kind.matches("[a-z]+") || !id.matches("[a-zA-Z0-9-]+")) throw new IllegalArgumentException("Invalid record name");
        return root.resolve(kind).resolve(id + ".json");
    }
}
