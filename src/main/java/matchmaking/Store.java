package matchmaking;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** One entity per file; an atomic rename makes each committed state durable. */
public final class Store {
    private final Path root;

    public Store(Path root) { this.root = root; }

    public Properties read(String kind, String id) {
        Properties values = new Properties();
        Path file = file(kind, id);
        if (!Files.exists(file)) return values;
        try (InputStream in = Files.newInputStream(file)) {
            values.load(in);
            return values;
        } catch (IOException e) { throw new IllegalStateException("Cannot read " + file, e); }
    }

    public void write(String kind, String id, Properties values) {
        Path file = file(kind, id);
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), "record-", ".tmp");
            try {
                try (OutputStream out = Files.newOutputStream(temp)) { values.store(out, null); }
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temp); }
        } catch (IOException e) { throw new IllegalStateException("Cannot write " + file, e); }
    }

    private Path file(String kind, String id) {
        if (!kind.matches("[a-z]+") || !id.matches("[a-zA-Z0-9-]+")) throw new IllegalArgumentException("Invalid record name");
        return root.resolve(kind).resolve(id + ".properties");
    }
}
