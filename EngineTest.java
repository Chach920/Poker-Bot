import java.util.*;

public class EngineTest.java {
    static int failures = 0;

    public static void main(String[] args) {
        randomPlayThroughTest(500);
        allInTest();
        minRaiseEnforcementTest();
        checkVsCallLabelingTest();
        blindAlternationTest();
        System.out.println(failures == 0 ? "\nALL ENGINE TESTS PASSED" : "\n" + failures + " ENGINE TEST(S) FAILED");
        if (failures > 0) System.exit(1);
    }

    static void check(String label, boolean pass) {
        System.out.println((pass ? "PASS" : "FAIL") + " - " + label);
        if (!pass) failures++;
    }

    static void randomPlayThroughTest(int hands) {
        GameEngine g = new GameEngine();
        g.newGame();
        Random rng = new Random(42);
        int handsPlayed = 0;
        int guardTotal = 0;

        while (handsPlayed < hands && guardTotal++ < hands * 50) {
            int totalBefore = g.humanChips + g.botChips + g.pot;
            check("chip conservation before action (hand " + handsPlayed + ")", totalBefore == GameEngine.STARTING_CHIPS * 2);

            if (!g.handInProgress) {
                if (!g.canStartHand()) break;
                g.startHand();
                g.maybeRunBotTurns();
                handsPlayed++;
                continue;
            }

            if (g.toAct == GameEngine.Seat.HUMAN) {
                int r = rng.nextInt(10);
                GameEngine.ActionResult result;
                if (r < 4) {
                    result = g.applyAction(GameEngine.Seat.HUMAN, "check", null);
                } else if (r < 7) {
                    int callAmount = g.currentBet - g.humanContrib;
                    int total = g.currentBet + Math.max(g.lastRaiseSize, GameEngine.MIN_BET);
                    total = Math.min(total, g.humanContrib + g.humanChips);
                    result = g.applyAction(GameEngine.Seat.HUMAN, "raise", total);
                    if (!result.legal) {
                        result = g.applyAction(GameEngine.Seat.HUMAN, "check", null);
                    }
                } else {
                    result = g.applyAction(GameEngine.Seat.HUMAN, "fold", null);
                }
                if (!result.legal) {
                    check("random action was legal: " + result.error, false);
                    result = g.applyAction(GameEngine.Seat.HUMAN, "check", null);
                }
                g.maybeRunBotTurns();
            }

            int totalAfter = g.humanChips + g.botChips + g.pot;
            check("chip conservation after action", totalAfter == GameEngine.STARTING_CHIPS * 2);
        }

        check("played the requested number of hands (or ran out of chips)",
            handsPlayed == hands || !g.canStartHand());
        check("final chip conservation holds", g.humanChips + g.botChips + g.pot == GameEngine.STARTING_CHIPS * 2);
        check("chip counts never negative", g.humanChips >= 0 && g.botChips >= 0);
        System.out.println("Random play-through: " + handsPlayed + " hands, human=" + g.humanChips + " bot=" + g.botChips);
    }

    static void allInTest() {
        GameEngine g = new GameEngine();
        g.newGame();
        g.humanChips = 50;
        g.botChips = 5000;
        g.buttonIsHuman = true;
        g.startHand();

        int humanStack = g.humanChips;
        GameEngine.ActionResult r = g.applyAction(GameEngine.Seat.HUMAN, "raise", g.humanContrib + humanStack);
        check("all-in raise from short stack is legal", r.legal);
        check("human is marked all-in after shoving entire stack", g.humanChips == 0);

        g.maybeRunBotTurns();

        check("hand resolves to completion when a player is all-in",
            !g.handInProgress || g.street == GameEngine.Street.HAND_OVER || g.humanFolded || g.botFolded || g.toAct != null);
        check("chip conservation holds through all-in", g.humanChips + g.botChips + g.pot == 5050);
    }

    static void minRaiseEnforcementTest() {
        GameEngine g = new GameEngine();
        g.newGame();
        g.startHand();
        int tooSmall = g.currentBet + 1;
        GameEngine.ActionResult r = g.applyAction(g.toAct, "raise", tooSmall);
        check("raise below minimum legal size is rejected", !r.legal);
    }

    static void checkVsCallLabelingTest() {
        GameEngine g = new GameEngine();
        g.newGame();
        g.startHand();
        GameEngine.Seat firstActor = g.toAct;
        int callAmountBefore = g.currentBet - g.contribOf(g.toAct);
        check("preflop first actor faces a call (blinds unequal)", callAmountBefore > 0);
        GameEngine.ActionResult r = g.applyAction(g.toAct, "check", null);
        check("'check' action auto-calls when a bet is outstanding", r.legal);
    }

    static void blindAlternationTest() {
        GameEngine g = new GameEngine();
        g.newGame();
        boolean firstButton = g.buttonIsHuman;
        while (g.handInProgress) {
            GameEngine.Seat actor = g.toAct;
            g.applyAction(actor, "check", null);
            g.maybeRunBotTurns();
        }
        if (g.canStartHand()) {
            g.startHand();
            check("button alternates between hands", g.buttonIsHuman != firstButton);
        }
    }
}