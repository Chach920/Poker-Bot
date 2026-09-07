import java.util.*;

class GameEngine.java {
    static final int STARTING_CHIPS = 2000;
    static final int SMALL_BLIND = 10;
    static final int BIG_BLIND = 20;
    static final int MIN_BET = 10;

    enum Street { PREFLOP, FLOP, TURN, RIVER, SHOWDOWN, HAND_OVER }
    enum Seat { HUMAN, BOT }

    int humanChips = STARTING_CHIPS;
    int botChips = STARTING_CHIPS;
    int pot = 0;
    boolean buttonIsHuman = true;

    List<Card> deck = new ArrayList<>();
    List<Card> humanHole = new ArrayList<>();
    List<Card> botHole = new ArrayList<>();
    List<Card> board = new ArrayList<>();

    Street street = Street.PREFLOP;
    int humanContrib = 0;
    int botContrib = 0;
    int currentBet = 0;
    int lastRaiseSize = BIG_BLIND;
    boolean humanActed = false;
    boolean botActed = false;
    Seat toAct = Seat.HUMAN;
    boolean handInProgress = false;
    boolean humanFolded = false;
    boolean botFolded = false;
    boolean humanAllIn = false;
    boolean botAllIn = false;
    String winner = null;
    String winningHandDescription = null;
    List<String> log = new ArrayList<>();
    Random rng = new Random();

    void newGame() {
        humanChips = STARTING_CHIPS;
        botChips = STARTING_CHIPS;
        buttonIsHuman = true;
        log.clear();
        log.add("New game. Both players start with " + STARTING_CHIPS + " chips.");
        startHand();
    }

    boolean canStartHand() {
        return humanChips >= MIN_BET && botChips >= MIN_BET;
    }

    void startHand() {
        if (!canStartHand()) {
            handInProgress = false;
            street = Street.HAND_OVER;
            winner = humanChips >= MIN_BET ? "human" : (botChips >= MIN_BET ? "bot" : "draw");
            log.add("Game over - a player is out of chips.");
            return;
        }

        deck = GameEngineDeck.freshShuffledDeck(rng);
        humanHole = new ArrayList<>();
        botHole = new ArrayList<>();
        board = new ArrayList<>();
        int next = 0;
        humanHole.add(deck.get(next++));
        botHole.add(deck.get(next++));
        humanHole.add(deck.get(next++));
        botHole.add(deck.get(next++));
        deck = new ArrayList<>(deck.subList(next, deck.size()));

        street = Street.PREFLOP;
        humanContrib = 0;
        botContrib = 0;
        currentBet = 0;
        lastRaiseSize = BIG_BLIND;
        humanActed = false;
        botActed = false;
        humanFolded = false;
        botFolded = false;
        humanAllIn = false;
        botAllIn = false;
        pot = 0;
        winner = null;
        winningHandDescription = null;
        handInProgress = true;

        int sbAmount = Math.min(SMALL_BLIND, buttonIsHuman ? humanChips : botChips);
        int bbAmount = Math.min(BIG_BLIND, buttonIsHuman ? botChips : humanChips);
        if (buttonIsHuman) {
            postBlind(Seat.HUMAN, sbAmount);
            postBlind(Seat.BOT, bbAmount);
        } else {
            postBlind(Seat.BOT, sbAmount);
            postBlind(Seat.HUMAN, bbAmount);
        }
        currentBet = Math.max(sbAmount, bbAmount);

        toAct = buttonIsHuman ? Seat.HUMAN : Seat.BOT;
        log.add((buttonIsHuman ? "Human" : "Bot") + " is on the button.");
        log.add("New hand dealt. Blinds posted (" + SMALL_BLIND + "/" + BIG_BLIND + ").");
    }

    private void postBlind(Seat seat, int amount) {
        if (seat == Seat.HUMAN) {
            humanChips -= amount;
            humanContrib += amount;
            if (humanChips == 0) humanAllIn = true;
        } else {
            botChips -= amount;
            botContrib += amount;
            if (botChips == 0) botAllIn = true;
        }
        pot += amount;
    }

    int chipsOf(Seat seat) { return seat == Seat.HUMAN ? humanChips : botChips; }
    int contribOf(Seat seat) { return seat == Seat.HUMAN ? humanContrib : botContrib; }
    boolean foldedOf(Seat seat) { return seat == Seat.HUMAN ? humanFolded : botFolded; }
    boolean allInOf(Seat seat) { return seat == Seat.HUMAN ? humanAllIn : botAllIn; }
    Seat other(Seat seat) { return seat == Seat.HUMAN ? Seat.BOT : Seat.HUMAN; }

    static class ActionResult {
        boolean legal;
        String error;
        ActionResult(boolean legal, String error) { this.legal = legal; this.error = error; }
        static ActionResult ok() { return new ActionResult(true, null); }
        static ActionResult bad(String msg) { return new ActionResult(false, msg); }
    }

    ActionResult applyAction(Seat seat, String action, Integer raiseTo) {
        if (!handInProgress) return ActionResult.bad("No hand in progress.");
        if (toAct != seat) return ActionResult.bad("It is not your turn.");
        if (foldedOf(seat)) return ActionResult.bad("You already folded.");

        int callAmount = currentBet - contribOf(seat);

        switch (action) {
            case "fold": {
                if (callAmount <= 0) {
                    doCheckOrCall(seat);
                    log.add(displayName(seat) + " checked (nothing to fold to).");
                    break;
                }
                setFolded(seat, true);
                log.add(displayName(seat) + " folded.");
                endHandByFold(other(seat));
                return ActionResult.ok();
            }
            case "check": {
                if (callAmount < 0) return ActionResult.bad("Invalid state.");
                doCheckOrCall(seat);
                if (callAmount == 0) {
                    log.add(displayName(seat) + " checked.");
                } else {
                    log.add(displayName(seat) + " called " + callAmount + ".");
                }
                break;
            }
            case "raise": {
                if (raiseTo == null) return ActionResult.bad("Raise requires an amount.");
                int stack = chipsOf(seat);
                int maxPossibleTotal = contribOf(seat) + stack;
                int minLegalTotal = currentBet + Math.max(lastRaiseSize, MIN_BET);
                if (currentBet == 0 && raiseTo < MIN_BET) {
                    return ActionResult.bad("Minimum bet is " + MIN_BET + ".");
                }
                if (currentBet > 0 && raiseTo < Math.min(minLegalTotal, maxPossibleTotal)) {
                    return ActionResult.bad("Minimum raise is to " + minLegalTotal + ".");
                }
                if (raiseTo > maxPossibleTotal) raiseTo = maxPossibleTotal;
                int added = raiseTo - contribOf(seat);
                if (added > stack) added = stack;
                int newTotal = contribOf(seat) + added;
                int raiseSize = newTotal - currentBet;
                applyChips(seat, added);
                if (newTotal > currentBet) {
                    currentBet = newTotal;
                    lastRaiseSize = Math.max(raiseSize, MIN_BET);
                    setActed(seat, true);
                    setActed(other(seat), false);
                }
                if (chipsOf(seat) == 0) setAllIn(seat, true);
                log.add(displayName(seat) + (currentBet == added ? " bet " : " raised to ") + currentBet + ".");
                break;
            }
            default:
                return ActionResult.bad("Unknown action: " + action);
        }

        advanceAfterAction(seat);
        return ActionResult.ok();
    }

    private void doCheckOrCall(Seat seat) {
        int callAmount = currentBet - contribOf(seat);
        if (callAmount > 0) {
            int stack = chipsOf(seat);
            int actual = Math.min(callAmount, stack);
            applyChips(seat, actual);
            if (chipsOf(seat) == 0) setAllIn(seat, true);
        }
        setActed(seat, true);
    }

    private void applyChips(Seat seat, int amount) {
        if (seat == Seat.HUMAN) {
            humanChips -= amount;
            humanContrib += amount;
        } else {
            botChips -= amount;
            botContrib += amount;
        }
        pot += amount;
    }

    private void setActed(Seat seat, boolean val) {
        if (seat == Seat.HUMAN) humanActed = val; else botActed = val;
    }

    private void setFolded(Seat seat, boolean val) {
        if (seat == Seat.HUMAN) humanFolded = val; else botFolded = val;
    }

    private void setAllIn(Seat seat, boolean val) {
        if (seat == Seat.HUMAN) humanAllIn = val; else botAllIn = val;
    }

    private String displayName(Seat seat) { return seat == Seat.HUMAN ? "Human" : "Bot"; }

    private void advanceAfterAction(Seat seat) {
        if (!handInProgress) return;

        boolean contribEqual = humanContrib == botContrib;
        boolean bothActed = humanActed && botActed;
        boolean someoneAllIn = humanAllIn || botAllIn;

        if (someoneAllIn && contribEqual) {
            runOutRemainingStreets();
            return;
        }

        if (contribEqual && bothActed) {
            advanceStreet();
            return;
        }

        toAct = other(seat);
    }

    private void runOutRemainingStreets() {
        while (street != Street.RIVER && street != Street.SHOWDOWN && street != Street.HAND_OVER) {
            dealNextStreetCards();
        }
        if (street == Street.RIVER || street == Street.SHOWDOWN) {
            resolveShowdown();
        }
    }

    private void advanceStreet() {
        if (street == Street.RIVER) {
            resolveShowdown();
            return;
        }
        dealNextStreetCards();
        humanContrib = 0;
        botContrib = 0;
        currentBet = 0;
        lastRaiseSize = BIG_BLIND;
        humanActed = false;
        botActed = false;
        toAct = buttonIsHuman ? Seat.BOT : Seat.HUMAN;
    }

    private void dealNextStreetCards() {
        switch (street) {
            case PREFLOP:
                board.add(deck.remove(0));
                board.add(deck.remove(0));
                board.add(deck.remove(0));
                street = Street.FLOP;
                log.add("Flop: " + cardListToString(board));
                break;
            case FLOP:
                board.add(deck.remove(0));
                street = Street.TURN;
                log.add("Turn: " + cardListToString(board));
                break;
            case TURN:
                board.add(deck.remove(0));
                street = Street.RIVER;
                log.add("River: " + cardListToString(board));
                break;
            default:
                break;
        }
    }

    private String cardListToString(List<Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (Card c : cards) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(c.toString());
        }
        return sb.toString();
    }

    private void endHandByFold(Seat winnerSeat) {
        handInProgress = false;
        street = Street.HAND_OVER;
        if (winnerSeat == Seat.HUMAN) {
            humanChips += pot;
            winner = "human";
        } else {
            botChips += pot;
            winner = "bot";
        }
        winningHandDescription = "Opponent folded";
        log.add(displayName(winnerSeat) + " wins the pot of " + pot + " (opponent folded).");
        pot = 0;
        buttonIsHuman = !buttonIsHuman;
    }

    private void resolveShowdown() {
        street = Street.SHOWDOWN;
        List<Card> humanBest = new ArrayList<>(humanHole);
        humanBest.addAll(board);
        List<Card> botBest = new ArrayList<>(botHole);
        botBest.addAll(board);

        int cmp = PokerTime.compareBestHands(humanBest, botBest);
        String humanDesc = PokerTime.describeBestHand(humanBest);
        String botDesc = PokerTime.describeBestHand(botBest);

        if (cmp > 0) {
            humanChips += pot;
            winner = "human";
            winningHandDescription = humanDesc;
            log.add("Showdown: Human wins with " + humanDesc + " (bot had " + botDesc + ").");
        } else if (cmp < 0) {
            botChips += pot;
            winner = "bot";
            winningHandDescription = botDesc;
            log.add("Showdown: Bot wins with " + botDesc + " (human had " + humanDesc + ").");
        } else {
            int half = pot / 2;
            humanChips += half;
            botChips += pot - half;
            winner = "split";
            winningHandDescription = humanDesc;
            log.add("Showdown: split pot, both have " + humanDesc + ".");
        }
        pot = 0;
        handInProgress = false;
        street = Street.HAND_OVER;
        buttonIsHuman = !buttonIsHuman;
    }

    void maybeRunBotTurns() {
        int guard = 0;
        while (handInProgress && toAct == Seat.BOT && guard++ < 20) {
            runOneBotAction();
        }
    }

    private void runOneBotAction() {
        double handStrength = PokerTime.cardStrength(botHole.get(0), botHole.get(1), board);
        double boardStrength = PokerTime.boardStrength(board);
        double predicted = PokerTime.predictFutureAggression(board);
        PokerTime.Action decision = PokerTime.decideAction(handStrength, boardStrength, predicted);

        int callAmount = currentBet - botContrib;

        if (decision == PokerTime.Action.FOLD && callAmount <= 0) {
            decision = PokerTime.Action.CHECK;
        }

        if (decision == PokerTime.Action.FOLD) {
            applyAction(Seat.BOT, "fold", null);
            return;
        }

        if (decision == PokerTime.Action.CHECK) {
            applyAction(Seat.BOT, "check", null);
            return;
        }

        double aggression = PokerTime.aggressionLevel(handStrength, boardStrength);
        int betSize = MIN_BET + (int) Math.round((aggression / 100.0) * Math.max(pot, MIN_BET) * 0.75);
        betSize = Math.max(betSize, MIN_BET);
        int desiredTotal = currentBet + betSize;
        int maxPossibleTotal = botContrib + botChips;
        if (desiredTotal > maxPossibleTotal) desiredTotal = maxPossibleTotal;

        int minLegalTotal = currentBet + Math.max(lastRaiseSize, MIN_BET);
        if (currentBet > 0 && desiredTotal < Math.min(minLegalTotal, maxPossibleTotal)) {
            desiredTotal = Math.min(minLegalTotal, maxPossibleTotal);
        }
        if (currentBet == 0 && desiredTotal < MIN_BET) {
            desiredTotal = Math.min(MIN_BET, maxPossibleTotal);
        }

        ActionResult result = applyAction(Seat.BOT, "raise", desiredTotal);
        if (!result.legal) {
            applyAction(Seat.BOT, "check", null);
        }
    }
}

class GameEngineDeck {
    static List<Card> freshShuffledDeck(Random rng) {
        List<Card> deck = PokerTime.createDeck();
        Collections.shuffle(deck, rng);
        return deck;
    }
}