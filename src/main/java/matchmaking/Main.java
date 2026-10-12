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
        MatchmakingFacade common = new MatchmakingFacade(parties, servers, matches, new WorkflowRepository(store));
        AdminFacade admin = new AdminFacade(common, parties, servers, matches);
        Scanner in = new Scanner(System.in);
        String lastId = "";
        System.out.println("\nMATCHMAKING - START A MATCH");
        System.out.println("Data folder: " + data.toAbsolutePath());
        while (true) {
            System.out.println("\n----------------------------------------");
            System.out.println("1 - Start a match (all three steps)");
            System.out.println("2 - Reserve a party (step A only)");
            System.out.println("3 - Continue a request (next step)");
            System.out.println("4 - View saved request status");
            System.out.println("5 - Cancel a request");
            System.out.println("6 - Demo: server failure (undo party reservation)");
            System.out.println("7 - Demo: anti-cheat failure (release server, then cancel party)");
            System.out.println("0 - Exit");
            System.out.print("Choose an option: ");
            if (!in.hasNextLine()) return;
            String choice = in.nextLine().trim();
            if (choice.equals("0")) {
                System.out.println("Goodbye. Your saved requests remain on disk.");
                return;
            }
            try {
                if (choice.equals("1") || choice.equals("2") || choice.equals("6") || choice.equals("7")) {
                    String player = read(in, "Player name: ");
                    while (player.isBlank()) player = read(in, "Name cannot be empty. Enter a player name: ");
                    int rating = number(in, "Skill rating [0-3000]: ", 0, 3000);
                    int size = number(in, "Party size [1-5 players]: ", 1, 5);
                    boolean demo = choice.equals("6") || choice.equals("7");
                    int ping = demo ? (choice.equals("6") ? 150 : 50)
                            : number(in, "Ping in ms [0 or higher; above 120 fails the server check]: ", 0, Integer.MAX_VALUE);
                    boolean suspicious = demo ? choice.equals("7") : yesNo(in);
                    if (demo) System.out.println("Demo settings: ping=" + ping + " ms, suspicious player=" + (suspicious ? "yes" : "no"));
                    Workflow flow = common.begin(player, rating, size, ping, suspicious);
                    lastId = flow.id();
                    System.out.println("\nRequest created. ID: " + lastId);
                    System.out.println("Step A complete: party reserved.");
                    showState(admin, lastId);
                    if (!choice.equals("2")) {
                        try {
                            common.advance(lastId);
                            System.out.println("\nStep B complete: server allocated.");
                            showState(admin, lastId);
                            common.advance(lastId);
                            System.out.println("\nStep C complete: anti-cheat passed, match confirmed.");
                        } catch (StageRejected e) { System.out.println("\nRequest rejected: " + reason(e)); }
                        showState(admin, lastId);
                    } else {
                        System.out.println("Choose option 3 to continue. Press Enter at the ID prompt to use this request.");
                    }
                } else if (choice.equals("3") || choice.equals("4") || choice.equals("5")) {
                    String id = read(in, "Request ID" + (lastId.isEmpty() ? ": " : " [Enter = last request]: "));
                    if (id.isEmpty()) id = lastId;
                    if (id.isEmpty()) { System.out.println("Create a request first, or enter an existing request ID."); continue; }
                    common.require(id);
                    lastId = id;
                    try {
                        if (choice.equals("3")) common.advance(id);
                        if (choice.equals("5")) admin.compensate(id);
                    } catch (StageRejected e) { System.out.println("Request rejected: " + reason(e)); }
                    showState(admin, id);
                } else System.out.println("Unknown option. Choose a number from the menu.");
            } catch (NoSuchElementException e) {
                return;
            } catch (StageRejected e) {
                System.out.println("Request rejected: " + reason(e));
            } catch (RuntimeException e) {
                System.out.println("Could not complete the action: " + e.getMessage());
            }
        }
    }

    private static String read(Scanner in, String prompt) {
        System.out.print(prompt);
        return in.nextLine().trim();
    }

    private static int number(Scanner in, String prompt, int min, int max) {
        while (true) {
            try {
                int value = Integer.parseInt(read(in, prompt));
                if (value >= min && value <= max) return value;
            } catch (NumberFormatException ignored) { }
            System.out.println("Enter a whole number from " + min + (max == Integer.MAX_VALUE ? " or higher." : " to " + max + "."));
        }
    }

    private static boolean yesNo(Scanner in) {
        while (true) {
            String value = read(in, "Anti-cheat: is the player suspicious? [yes/no]: ");
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

    private static String reason(StageRejected error) {
        String message = error.getMessage();
        if (message.startsWith("Workflow not found:")) return "No request found with this ID.";
        return switch (message) {
            case "No suitable server: ping above 120 ms" -> "Ping exceeds 120 ms. No server allocated; party reservation cancelled.";
            case "Anti-cheat rejected a suspicious player" -> "Anti-cheat rejected the player. Server released, then party reservation cancelled.";
            case "Player name is required" -> "Enter a player name.";
            case "Skill rating must be 0..3000" -> "Skill rating must be between 0 and 3000.";
            case "Party size must be 1..5" -> "Party size must be between 1 and 5 players.";
            default -> message;
        };
    }
}
