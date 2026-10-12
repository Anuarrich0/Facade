package matchmaking;

import java.nio.file.Path;
import java.util.Scanner;
import java.util.NoSuchElementException;
import matchmaking.party.*;
import matchmaking.server.*;
import matchmaking.match.*;

public final class Main {
    public static void main(String[] args) {
        Path data = Path.of(args.length == 0 ? "data" : args[0]);
        Store store = new Store(data);
        PartyService parties = new PartyService(new PartyRepository(store), new RatingRule());
        ServerService servers = new ServerService(new ServerRepository(store), new LatencyRule());
        MatchService matches = new MatchService(new MatchRepository(store), new AntiCheatRule());
        MatchmakingFacade common = new MatchmakingFacade(parties, servers, matches, store);
        AdminFacade admin = new AdminFacade(common, parties, servers, matches);
        Scanner in = new Scanner(System.in);
        System.out.println("\nMATCHMAKING");
        System.out.println("Data folder: " + data.toAbsolutePath());
        while (true) {
            System.out.println("\n----------------------------------------");
            System.out.println("1 - Create a match request");
            System.out.println("2 - Perform the next step");
            System.out.println("3 - View saved status");
            System.out.println("4 - Cancel a request");
            System.out.println("0 - Exit");
            System.out.print("Choose an option: ");
            if (!in.hasNextLine()) return;
            String choice = in.nextLine().trim();
            if (choice.equals("0")) {
                System.out.println("Goodbye. Your saved requests remain on disk.");
                return;
            }
            String id = null;
            try {
                if (choice.equals("1")) {
                    String player = read(in, "Player name: ");
                    int rating = number(in, "Skill rating [0-3000]: ");
                    int size = number(in, "Party size [1-5]: ");
                    int ping = number(in, "Ping in ms [above 120 rejects step B]: ");
                    boolean suspicious = yesNo(in, "Suspicious player? [yes rejects step C; yes/no]: ");
                    boolean runAll = yesNo(in, "Run all steps now? [yes/no; no stops after party reservation]: ");
                    Workflow flow = common.begin(player, rating, size, ping, suspicious);
                    id = flow.id();
                    System.out.println("Request created. ID: " + id);
                    if (runAll) {
                        common.advance(id);
                        common.advance(id);
                    }
                } else if (choice.equals("2") || choice.equals("3") || choice.equals("4")) {
                    id = read(in, "Request ID: ");
                    common.require(id);
                    if (choice.equals("2")) common.advance(id);
                    if (choice.equals("4")) admin.compensate(id);
                } else {
                    System.out.println("Choose 0, 1, 2, 3 or 4.");
                    continue;
                }
            } catch (NoSuchElementException e) {
                return;
            } catch (StageRejected e) {
                System.out.println("Request rejected: " + e.getMessage());
            } catch (RuntimeException e) {
                System.out.println("Could not complete the action: " + e.getMessage());
            }
            if (id != null) {
                try { showState(admin, id); }
                catch (RuntimeException e) { System.out.println("Status unavailable: " + e.getMessage()); }
            }
        }
    }

    private static String read(Scanner in, String prompt) {
        System.out.print(prompt);
        return in.nextLine().trim();
    }

    private static int number(Scanner in, String prompt) {
        while (true) {
            try {
                return Integer.parseInt(read(in, prompt));
            } catch (NumberFormatException ignored) { }
            System.out.println("Enter a whole number.");
        }
    }

    private static boolean yesNo(Scanner in, String prompt) {
        while (true) {
            String value = read(in, prompt);
            if (value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("true")) return true;
            if (value.equalsIgnoreCase("no") || value.equalsIgnoreCase("false")) return false;
            System.out.println("Enter yes or no.");
        }
    }

    private static void showState(AdminFacade admin, String id) {
        System.out.println("Saved status for request " + id + ":");
        for (String field : admin.inspect(id).split(", ")) {
            String[] parts = field.split("=", 2);
            String label = switch (parts[0]) {
                case "workflow" -> "Request";
                case "party" -> "Party (A)";
                case "server" -> "Server (B)";
                default -> "Match (C)";
            };
            String status = switch (parts[1]) {
                case "PENDING_B" -> "waiting for server allocation";
                case "PENDING_C" -> "waiting for anti-cheat check";
                case "COMPLETED" -> "completed successfully";
                case "COMPENSATING" -> "cancellation in progress";
                case "COMPENSATED" -> "cancelled";
                case "RESERVED" -> "reserved";
                case "CANCELLED" -> "reservation cancelled";
                case "ALLOCATED" -> "allocated";
                case "RELEASED" -> "released";
                case "CONFIRMED" -> "confirmed";
                case "REVOKED" -> "confirmation revoked";
                case "NONE" -> "not created";
                default -> parts[1];
            };
            System.out.println("  " + label + ": " + status);
        }
    }

}
