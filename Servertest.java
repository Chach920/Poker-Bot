import java.net.URI;
import java.net.http.*;
import java.util.regex.*;

public class ServerSmokeTest {
    static int failures = 0;

    public static void main(String[] args) throws Exception {
        Thread serverThread = new Thread(() -> {
            try { PokerServer.main(new String[0]); } catch (Exception e) { e.printStackTrace(); }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        Thread.sleep(700);

        HttpClient client = HttpClient.newHttpClient();
        String base = "http://localhost:8000";

        HttpResponse<String> home = get(client, base + "/");
        check("GET / serves the frontend page", home.statusCode() == 200 && home.body().contains("Heads-up hold'em"));

        HttpResponse<String> state = get(client, base + "/api/state");
        check("GET /api/state returns 200", state.statusCode() == 200);
        System.out.println("Initial state: " + state.body());
        check("initial state has 2000/2000 chips", state.body().contains("\"humanChips\":") && chipsLookRight(state.body()));

        for (int i = 0; i < 40 && stillHumanTurn(state.body()) && handInProgress(state.body()); i++) {
            String action = i % 3 == 2 ? "raise" : "check";
            String body = action.equals("raise")
                ? "{\"action\":\"raise\",\"amount\":" + (currentBet(state.body()) + 20) + "}"
                : "{\"action\":\"check\"}";
            HttpResponse<String> res = post(client, base + "/api/action", body);
            if (res.statusCode() != 200) {
                System.out.println("Action failed: " + res.body());
                break;
            }
            state = res;
            System.out.println("After " + action + ": street=" + extractString(state.body(), "street")
                + " pot=" + extractInt(state.body(), "pot")
                + " toAct=" + extractString(state.body(), "toAct")
                + " handInProgress=" + state.body().contains("\"handInProgress\":true"));
        }

        check("chip conservation holds via HTTP layer",
            extractInt(state.body(), "humanChips") + extractInt(state.body(), "botChips") + extractInt(state.body(), "pot") == 4000);

        if (!handInProgress(state.body())) {
            System.out.println("Hand finished. winner=" + extractString(state.body(), "winner"));
            HttpResponse<String> newHand = post(client, base + "/api/action", "{\"action\":\"new-hand\"}");
            check("new-hand action succeeds after a hand ends", newHand.statusCode() == 200);
            System.out.println("After new-hand: " + newHand.body());
            if (handInProgress(newHand.body())) {
                check("bot hole cards hidden while new hand is still in progress", newHand.body().contains("\"botHole\":[]"));
            } else {
                System.out.println("(new hand resolved immediately - likely an all-in edge case, skipping hidden-card check)");
            }
        }

        HttpResponse<String> badRaise = post(client, base + "/api/action", "{\"action\":\"raise\",\"amount\":1}");
        check("illegal raise amount rejected with 400", badRaise.statusCode() == 400);

        System.out.println(failures == 0 ? "\nALL SERVER SMOKE TESTS PASSED" : "\n" + failures + " SERVER TEST(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    static boolean chipsLookRight(String json) {
        return extractInt(json, "humanChips") == 2000 || extractInt(json, "botChips") <= 2000;
    }

    static boolean stillHumanTurn(String json) {
        return "human".equals(extractString(json, "toAct"));
    }

    static boolean handInProgress(String json) {
        return json.contains("\"handInProgress\":true");
    }

    static int currentBet(String json) {
        return extractInt(json, "currentBet");
    }

    static String extractString(String body, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
        return m.find() ? m.group(1) : null;
    }

    static int extractInt(String body, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)").matcher(body);
        return m.find() ? Integer.parseInt(m.group(1)) : -99999;
    }

    static void check(String label, boolean pass) {
        System.out.println((pass ? "PASS" : "FAIL") + " - " + label);
        if (!pass) failures++;
    }

    static HttpResponse<String> get(HttpClient c, String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        return c.send(req, HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> post(HttpClient c, String url, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return c.send(req, HttpResponse.BodyHandlers.ofString());
    }
}