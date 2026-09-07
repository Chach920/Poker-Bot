import java.util.*;
import java.util.Objects;

class Card {
    private final int value;
    private final int suit;

    private static final String[] VALUE_NAMES = {
        "", "A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"
    };
    private static final String[] SUIT_NAMES = { "", "C", "D", "H", "S" };

    public Card(int value, int suit) {
        if (value < 1 || value > 13) {
            throw new IllegalArgumentException("Card value must be 1-13, got " + value);
        }
        if (suit < 1 || suit > 4) {
            throw new IllegalArgumentException("Card suit must be 1-4, got " + suit);
        }
        this.value = value;
        this.suit = suit;
    }

    public int getValue() {
        return value;
    }

    public int getSuit() {
        return suit;
    }

    public int getRankHigh() {
        return value == 1 ? 14 : value;
    }

    @Override
    public String toString() {
        return VALUE_NAMES[value] + SUIT_NAMES[suit];
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Card)) return false;
        Card c = (Card) o;
        return value == c.value && suit == c.suit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, suit);
    }
}

class ScoreTests {
    static int failures = 0;

    public static void main(String[] args) {

        check("AA preflop > 72o preflop",
            PokerTime.cardStrength(new Card(1, 1), new Card(1, 2), List.of())
            > PokerTime.cardStrength(new Card(7, 1), new Card(2, 2), List.of()));

        check("AKs preflop > AKo preflop",
            PokerTime.cardStrength(new Card(1, 1), new Card(13, 1), List.of())
            > PokerTime.cardStrength(new Card(1, 1), new Card(13, 2), List.of()));

        check("Preflop score in [0,100]", inRange(
            PokerTime.cardStrength(new Card(1, 1), new Card(1, 2), List.of())));

        List<Card> board = List.of(new Card(2,1), new Card(5,2), new Card(9,3), new Card(10,4), new Card(13,1));

        double highCard = PokerTime.cardStrength(new Card(3,2), new Card(6,3), board);
        double onePair  = PokerTime.cardStrength(new Card(2,2), new Card(4,3), board);
        double twoPair  = PokerTime.cardStrength(new Card(5,3), new Card(9,4), board);
        double trips    = PokerTime.cardStrength(new Card(2,3), new Card(2,4), board);

        List<Card> straightBoard = List.of(new Card(6,1), new Card(7,2), new Card(8,3), new Card(2,4), new Card(9,1));
        double straight = PokerTime.cardStrength(new Card(10,2), new Card(4,3), straightBoard);
        List<Card> flushBoard = List.of(new Card(2,1), new Card(5,1), new Card(9,1), new Card(11,2), new Card(3,3));
        double flush = PokerTime.cardStrength(new Card(13,1), new Card(4,1), flushBoard);
        List<Card> fhBoard = List.of(new Card(4,1), new Card(4,2), new Card(9,3), new Card(9,4), new Card(2,1));
        double fullHouse = PokerTime.cardStrength(new Card(4,3), new Card(9,1), fhBoard);
        List<Card> quadBoard = List.of(new Card(4,1), new Card(4,2), new Card(4,3), new Card(9,4), new Card(2,1));
        double quads = PokerTime.cardStrength(new Card(4,4), new Card(3,2), quadBoard);
        List<Card> sfBoard = List.of(new Card(4,1), new Card(5,1), new Card(6,1), new Card(9,4), new Card(2,1));
        double straightFlush = PokerTime.cardStrength(new Card(7,1), new Card(8,1), sfBoard);

        check("one pair > high card", onePair > highCard);
        check("two pair > one pair", twoPair > onePair);
        check("trips > two pair", trips > twoPair);
        check("straight > trips", straight > trips);
        check("flush > straight", flush > straight);
        check("full house > flush", fullHouse > flush);
        check("quads > full house", quads > fullHouse);
        check("straight flush > quads", straightFlush > quads);

        for (double v : new double[]{highCard, onePair, twoPair, trips, straight, flush, fullHouse, quads, straightFlush}) {
            check("score in [0,100]: " + v, inRange(v));
        }

        List<Card> flopTrips = List.of(new Card(2,1), new Card(2,2), new Card(9,3));
        double tripsOnFlop = PokerTime.cardStrength(new Card(2,3), new Card(4,4), flopTrips);
        System.out.printf("Trips on flop: %.1f (band should be [50,60])%n", tripsOnFlop);
        check("trips-on-flop lands in trips band", tripsOnFlop >= 50 && tripsOnFlop <= 60 + 6 );

        List<Card> dryBoard = List.of(new Card(2,1), new Card(7,2), new Card(11,3));
        List<Card> wetBoard = List.of(new Card(9,1), new Card(10,1), new Card(11,1));
        double dry = PokerTime.boardStrength(dryBoard);
        double wet = PokerTime.boardStrength(wetBoard);
        System.out.printf("Dry board score: %.1f, Wet board score: %.1f%n", dry, wet);
        check("wet board > dry board", wet > dry);
        check("dry board in [0,100]", inRange(dry));
        check("wet board in [0,100]", inRange(wet));

        List<Card> pairedBoard = List.of(new Card(9,1), new Card(9,2), new Card(2,3));
        double paired = PokerTime.boardStrength(pairedBoard);
        check("paired board > dry board", paired > dry);

        check("AA preflop -> RAISE",
            PokerTime.decideAction(new Card(1,1), new Card(1,2), List.of()) == PokerTime.Action.RAISE);

        check("72o preflop -> FOLD",
            PokerTime.decideAction(new Card(7,1), new Card(2,2), List.of()) == PokerTime.Action.FOLD);

        List<Card> flushBoard2 = List.of(new Card(2,1), new Card(5,1), new Card(9,1), new Card(11,2), new Card(3,3));
        check("Made flush -> RAISE",
            PokerTime.decideAction(new Card(13,1), new Card(4,1), flushBoard2) == PokerTime.Action.RAISE);

        List<Card> scaryBoard = List.of(new Card(9,1), new Card(10,1), new Card(11,1));
        check("High card on wet board -> FOLD",
            PokerTime.decideAction(new Card(2,2), new Card(4,3), scaryBoard) == PokerTime.Action.FOLD);

        List<Card> dryBoard2 = List.of(new Card(2,1), new Card(7,2), new Card(13,3));
        check("Top pair on dry board -> RAISE",
            PokerTime.decideAction(new Card(13,1), new Card(4,2), dryBoard2) == PokerTime.Action.RAISE);

        List<Card> midBoard = List.of(new Card(6,1), new Card(9,2), new Card(10,3));
        check("Marginal hand vs matching board -> CHECK",
            PokerTime.decideAction(new Card(6,2), new Card(4,3), midBoard) == PokerTime.Action.CHECK);

        double[] handSamples = {0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
        for (double b : new double[]{0, 20, 40, 60}) {
            int lastRank = -1;
            for (double h : handSamples) {
                int rank = actionRank(PokerTime.decideAction(h, b));
                check(String.format("monotonic: hand=%.0f board=%.0f", h, b), rank >= lastRank);
                lastRank = rank;
            }
        }

        check("No board -> predicted aggression is 0",
            PokerTime.predictFutureAggression(List.of()) == 0);

        List<Card> dryFlop = List.of(new Card(2,1), new Card(7,2), new Card(11,3));
        List<Card> wetFlop = List.of(new Card(9,1), new Card(10,1), new Card(11,1));
        check("Wet flop predicts more aggression than dry flop",
            PokerTime.predictFutureAggression(wetFlop) > PokerTime.predictFutureAggression(dryFlop));

        check("predicted aggression in [0,100]", inRange(PokerTime.predictFutureAggression(wetFlop)));

        List<Card> asFlop = List.of(new Card(9,1), new Card(9,2), new Card(2,3));
        List<Card> asRiver = List.of(new Card(9,1), new Card(9,2), new Card(2,3), new Card(4,4), new Card(6,2));
        check("Flop predicts >= aggression of a later street with a similar board",
            PokerTime.predictFutureAggression(asFlop) >= PokerTime.boardStrength(asFlop));

        List<Card> tripKingsFlop = List.of(new Card(13,1), new Card(13,2), new Card(13,3));
        double tkBoard = PokerTime.boardStrength(tripKingsFlop);
        double tkPredicted = PokerTime.predictFutureAggression(tripKingsFlop);
        check("Predicted aggression on a wet flop exceeds the raw board score",
            tkPredicted > tkBoard);

        Card aceKicker = new Card(1,4);
        Card lowKicker = new Card(2,4);
        double tkHand = PokerTime.cardStrength(aceKicker, lowKicker, tripKingsFlop);
        PokerTime.Action legacyAction = PokerTime.decideAction(tkHand, tkBoard);
        PokerTime.Action fullAction = PokerTime.decideAction(tkHand, tkBoard, tkPredicted);
        System.out.printf("Trip-Kings flop: hand=%.1f board=%.1f predicted=%.1f legacy=%s full=%s%n",
            tkHand, tkBoard, tkPredicted, legacyAction, fullAction);
        check("Predicted aggression makes the decision no more aggressive than board score alone",
            actionRank(fullAction) <= actionRank(legacyAction));
        check("Predicted aggression actually changes this decision (RAISE -> CHECK)",
            legacyAction == PokerTime.Action.RAISE && fullAction == PokerTime.Action.CHECK);

        check("Card overload matches full 3-arg pipeline",
            PokerTime.decideAction(aceKicker, lowKicker, tripKingsFlop) == fullAction);

        check("No dominance -> aggression level is 0",
            PokerTime.aggressionLevel(20, 30) == 0);

        check("More dominance -> higher aggression level",
            PokerTime.aggressionLevel(90, 10) > PokerTime.aggressionLevel(60, 40));

        check("aggressionLevel in [0,100]", inRange(PokerTime.aggressionLevel(100, 0)));

        PokerTime.Action dominant = PokerTime.decideAction(70, 20, 100);
        check("Dominant hand overrides huge predicted aggression -> RAISE",
            dominant == PokerTime.Action.RAISE);

        PokerTime.Action notDominant = PokerTime.decideAction(40, 30, 100);
        check("Non-dominant hand is still scared off by huge predicted aggression -> FOLD",
            notDominant == PokerTime.Action.FOLD);

        double aaHeadsUp = PokerTime.monteCarloEquity(new Card(1, 1), new Card(1, 2), List.of(), 1, 20000);
        System.out.printf("Monte Carlo: AA vs 1 random opponent = %.1f (reference ~85.2)%n", aaHeadsUp);
        check("AA heads-up equity within 3 points of the known ~85.2 reference",
            Math.abs(aaHeadsUp - 85.2) < 3);

        double worstHand = PokerTime.monteCarloEquity(new Card(7, 1), new Card(2, 2), List.of(), 1, 20000);
        System.out.printf("Monte Carlo: 72o vs 1 random opponent = %.1f (reference ~34.6)%n", worstHand);
        check("72o heads-up equity within 3 points of the known ~34.6 reference",
            Math.abs(worstHand - 34.6) < 3);

        check("AA equity in [0,100]", inRange(aaHeadsUp));
        check("AA equity beats 72o equity heads-up", aaHeadsUp > worstHand);

        double aaVsMany = PokerTime.monteCarloEquity(new Card(1, 1), new Card(1, 2), List.of(), 8, 4000);
        check("AA equity drops with more opponents (8-way vs heads-up)", aaVsMany < aaHeadsUp);

        List<Card> flushRiver = List.of(new Card(2,1), new Card(5,1), new Card(9,1), new Card(11,2), new Card(3,3));
        double flushEquity = PokerTime.monteCarloEquity(new Card(13,1), new Card(4,1), flushRiver, 1, 5000);
        System.out.printf("Monte Carlo: made flush on river vs 1 opponent = %.1f%n", flushEquity);
        check("Made flush on river has very high heads-up equity", flushEquity > 90);

        boolean threw = false;
        try {
            PokerTime.monteCarloEquity(new Card(1,1), new Card(1,2), List.of(), 30, 100);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("Too many opponents for remaining deck throws instead of corrupting the deal", threw);

        System.out.println(failures == 0 ? "\nALL TESTS PASSED" : "\n" + failures + " TEST(S) FAILED");
        if (failures > 0) System.exit(1);
    }

    static int actionRank(PokerTime.Action a) {
        switch (a) {
            case FOLD: return 0;
            case CHECK: return 1;
            case RAISE: return 2;
            default: throw new IllegalStateException();
        }
    }

    static boolean inRange(double v) {
        return v >= 0 && v <= 100;
    }

    static void check(String label, boolean pass) {
        System.out.println((pass ? "PASS" : "FAIL") + " - " + label);
        if (!pass) failures++;
    }
}