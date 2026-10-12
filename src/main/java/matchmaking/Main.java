package matchmaking;

import java.nio.file.Path;
import java.util.Scanner;
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
        System.out.println("Matchmaking data: " + data.toAbsolutePath());
        while (true) {
            System.out.println("1 run  2 begin (A only)  3 advance  4 inspect  5 compensate  0 exit");
            String choice = in.nextLine().trim();
            if (choice.equals("0")) return;
            try {
                if (choice.equals("1") || choice.equals("2")) {
                    System.out.print("Player: "); String player = in.nextLine();
                    System.out.print("Skill rating 0..3000: "); int rating = Integer.parseInt(in.nextLine());
                    System.out.print("Party size 1..5: "); int size = Integer.parseInt(in.nextLine());
                    System.out.print("Ping ms (121+ rejects B): "); int ping = Integer.parseInt(in.nextLine());
                    System.out.print("Suspicious player? true/false (true rejects C): ");
                    String answer = in.nextLine().trim();
                    if (!answer.equalsIgnoreCase("true") && !answer.equalsIgnoreCase("false"))
                        throw new IllegalArgumentException("Enter true or false");
                    boolean suspicious = Boolean.parseBoolean(answer);
                    Workflow flow = common.begin(player, rating, size, ping, suspicious);
                    System.out.println("ID=" + flow.id());
                    if (choice.equals("1")) {
                        try { common.advance(flow.id()); common.advance(flow.id()); }
                        catch (StageRejected e) { System.out.println("Rejected: " + e.getMessage()); }
                    }
                    System.out.println(admin.inspect(flow.id()));
                } else if (choice.equals("3") || choice.equals("4") || choice.equals("5")) {
                    System.out.print("Workflow ID: "); String id = in.nextLine().trim();
                    if (choice.equals("3")) common.advance(id);
                    if (choice.equals("5")) admin.compensate(id);
                    System.out.println(admin.inspect(id));
                } else System.out.println("Unknown menu choice");
            } catch (RuntimeException e) { System.out.println("Rejected: " + e.getMessage()); }
        }
    }
}
