import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class PokerServer {
    static final int PORT = 8000;
    static final GameEngine game = new GameEngine();

    public static void main(String[] args) throws IOException {
        game.newGame();
        game.maybeRunBotTurns();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/state", new StateHandler());
        server.createContext("/api/action", new ActionHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Poker server running at http://localhost:" + PORT);
        System.out.println("Open that URL in a browser to play.");
    }

    static class StaticFileHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            Path file = Paths.get("frontend" + path);
            if (!Files.exists(file) || Files.isDirectory(file)) {
                byte[] body = "Not found".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
                return;
            }
            byte[] bytes = Files.readAllBytes(file);
            String contentType = path.endsWith(".html") ? "text/html; charset=utf-8"
                    : path.endsWith(".css") ? "text/css; charset=utf-8"
                    : path.endsWith(".js") ? "application/javascript; charset=utf-8"
                    : "application/octet-stream";
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }

    static class StateHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            sendJson(exchange, 200, JsonUtil.stateToJson(game));
        }
    }

    static class ActionHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"POST required\"}");
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String action = JsonUtil.extractString(body, "action");
            Integer amount = JsonUtil.extractInt(body, "amount");

            if (action == null) {
                sendJson(exchange, 400, "{\"error\":\"missing action\"}");
                return;
            }

            synchronized (game) {
                switch (action) {
                    case "new-game":
                        game.newGame();
                        game.maybeRunBotTurns();
                        break;
                    case "new-hand":
                        if (!game.handInProgress && game.canStartHand()) {
                            game.startHand();
                            game.maybeRunBotTurns();
                        }
                        break;
                    case "fold":
                    case "check":
                    case "raise": {
                        GameEngine.ActionResult result =
                            game.applyAction(GameEngine.Seat.HUMAN, action, amount);
                        if (!result.legal) {
                            sendJson(exchange, 400, "{\"error\":\"" + JsonUtil.escape(result.error) + "\"}");
                            return;
                        }
                        game.maybeRunBotTurns();
                        break;
                    }
                    default:
                        sendJson(exchange, 400, "{\"error\":\"unknown action\"}");
                        return;
                }
                sendJson(exchange, 200, JsonUtil.stateToJson(game));
            }
        }
    }

    static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}

class JsonUtil {
    static String extractString(String body, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
        return m.find() ? m.group(1) : null;
    }

    static Integer extractInt(String body, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)").matcher(body);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String str(String s) {
        return "\"" + escape(s) + "\"";
    }

    static String cardsArray(List<Card> cards) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < cards.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(str(cards.get(i).toString()));
        }
        return sb.append("]").toString();
    }

    static String logArray(List<String> log) {
        int start = Math.max(0, log.size() - 12);
        StringBuilder sb = new StringBuilder("[");
        for (int i = start; i < log.size(); i++) {
            if (i > start) sb.append(",");
            sb.append(str(log.get(i)));
        }
        return sb.append("]").toString();
    }

    static String stateToJson(GameEngine g) {
        boolean revealBotHole = !g.handInProgress;
        int callAmount = g.toAct == GameEngine.Seat.HUMAN ? g.currentBet - g.humanContrib : 0;
        int minRaiseTo = g.currentBet == 0
                ? GameEngine.MIN_BET
                : g.currentBet + Math.max(g.lastRaiseSize, GameEngine.MIN_BET);
        int humanMaxRaiseTo = g.humanContrib + g.humanChips;

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"handInProgress\":").append(g.handInProgress).append(",");
        sb.append("\"street\":").append(str(g.street.name())).append(",");
        sb.append("\"pot\":").append(g.pot).append(",");
        sb.append("\"humanChips\":").append(g.humanChips).append(",");
        sb.append("\"botChips\":").append(g.botChips).append(",");
        sb.append("\"humanContrib\":").append(g.humanContrib).append(",");
        sb.append("\"botContrib\":").append(g.botContrib).append(",");
        sb.append("\"currentBet\":").append(g.currentBet).append(",");
        sb.append("\"callAmount\":").append(Math.max(0, callAmount)).append(",");
        sb.append("\"minRaiseTo\":").append(Math.min(minRaiseTo, humanMaxRaiseTo)).append(",");
        sb.append("\"maxRaiseTo\":").append(humanMaxRaiseTo).append(",");
        sb.append("\"toAct\":").append(g.toAct == null ? "null" : str(g.toAct == GameEngine.Seat.HUMAN ? "human" : "bot")).append(",");
        sb.append("\"buttonIsHuman\":").append(g.buttonIsHuman).append(",");
        sb.append("\"humanHole\":").append(cardsArray(g.humanHole)).append(",");
        sb.append("\"botHole\":").append(revealBotHole ? cardsArray(g.botHole) : "[]").append(",");
        sb.append("\"board\":").append(cardsArray(g.board)).append(",");
        sb.append("\"humanFolded\":").append(g.humanFolded).append(",");
        sb.append("\"botFolded\":").append(g.botFolded).append(",");
        sb.append("\"winner\":").append(g.winner == null ? "null" : str(g.winner)).append(",");
        sb.append("\"winningHandDescription\":").append(g.winningHandDescription == null ? "null" : str(g.winningHandDescription)).append(",");
        sb.append("\"canStartHand\":").append(g.canStartHand()).append(",");
        sb.append("\"log\":").append(logArray(g.log));
        sb.append("}");
        return sb.toString();
    }
}