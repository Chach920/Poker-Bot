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

public class PokerTime {

    private static final double SCALE_MAX = 100.0;

    public enum Action { RAISE, CHECK, FOLD }

    private static final double RAISE_HAND_FLOOR = 65;
    private static final double FOLD_HAND_CEILING = 15;

    private static final double RAISE_EDGE = 15;
    private static final double FOLD_EDGE = -15;

    private static final double DOMINANCE_EDGE_THRESHOLD = 30;

    private static final double AGGRESSION_PER_CARD_TO_COME = 0.08;
    private static final double WET_BOARD_THRESHOLD = 40;
    private static final double WET_BOARD_AGGRESSION_BONUS = 8;

    public static double predictFutureAggression(List<Card> tableCards) {
        double board = boardStrength(tableCards);
        int size = (tableCards == null) ? 0 : tableCards.size();
        int cardsToCome = Math.max(0, 5 - size);

        double predicted = board * (1.0 + AGGRESSION_PER_CARD_TO_COME * cardsToCome);
        if (board >= WET_BOARD_THRESHOLD) {
            predicted += WET_BOARD_AGGRESSION_BONUS;
        }
        return clamp(predicted);
    }

    public static double aggressionLevel(double handStrength, double boardStrength) {
        double dominanceEdge = handStrength - boardStrength;
        if (dominanceEdge <= 0) return 0;
        return clamp((dominanceEdge / SCALE_MAX) * SCALE_MAX);
    }

    public static Action decideAction(double handStrength, double boardStrength, double predictedAggression) {
        if (handStrength >= RAISE_HAND_FLOOR) return Action.RAISE;
        if (handStrength <= FOLD_HAND_CEILING) return Action.FOLD;

        double dominanceEdge = handStrength - boardStrength;
        if (dominanceEdge >= DOMINANCE_EDGE_THRESHOLD) return Action.RAISE;

        double effectiveDanger = Math.max(boardStrength, predictedAggression);
        double edge = handStrength - effectiveDanger;
        if (edge >= RAISE_EDGE) return Action.RAISE;
        if (edge <= FOLD_EDGE) return Action.FOLD;
        return Action.CHECK;
    }

    public static Action decideAction(double handStrength, double boardStrength) {
        return decideAction(handStrength, boardStrength, boardStrength);
    }

    public static Action decideAction(Card playerCard1, Card playerCard2, List<Card> tableCards) {
        double hand = cardStrength(playerCard1, playerCard2, tableCards);
        double board = boardStrength(tableCards);
        double predictedAggression = predictFutureAggression(tableCards);
        return decideAction(hand, board, predictedAggression);
    }

    public static void main(String[] args) {
        int playerCount = 4;

        List<Card> deck = createDeck();
        Collections.shuffle(deck);

        List<List<Card>> holeCards = new ArrayList<>();
        for (int p = 0; p < playerCount; p++) {
            holeCards.add(new ArrayList<>(List.of(deck.remove(0), deck.remove(0))));
        }

        Card botCard1 = holeCards.get(0).get(0);
        Card botCard2 = holeCards.get(0).get(1);

        System.out.println("Bot hole cards: " + botCard1 + " " + botCard2);

        List<Card> tableCards = new ArrayList<>();
        System.out.printf("Preflop strength: %.1f / 100 -> %s%n",
                cardStrength(botCard1, botCard2, tableCards),
                decideAction(botCard1, botCard2, tableCards));

        for (int i = 0; i < 3; i++) tableCards.add(deck.remove(0));
        printStreet("Flop", botCard1, botCard2, tableCards);

        tableCards.add(deck.remove(0));
        printStreet("Turn", botCard1, botCard2, tableCards);

        tableCards.add(deck.remove(0));
        printStreet("River", botCard1, botCard2, tableCards);
    }

    private static void printStreet(String label, Card c1, Card c2, List<Card> tableCards) {
        double hand = cardStrength(c1, c2, tableCards);
        double board = boardStrength(tableCards);
        double predictedAggression = predictFutureAggression(tableCards);
        Action action = decideAction(hand, board, predictedAggression);
        System.out.println(label + " board: " + tableCards);
        System.out.printf("%s hand strength: %.1f / 100%n", label, hand);
        System.out.printf("%s board strength: %.1f / 100%n", label, board);
        System.out.printf("%s predicted future aggression: %.1f / 100%n", label, predictedAggression);
        System.out.printf("%s decision: %s%n", label, action);
        if (action == Action.RAISE) {
            System.out.printf("%s aggression level: %.1f / 100%n", label, aggressionLevel(hand, board));
        }
        double mcEquity = monteCarloEquity(c1, c2, tableCards, 3, 2000);
        System.out.printf("%s Monte Carlo equity vs 3 opponents (2000 trials): %.1f / 100%n", label, mcEquity);
    }

    public static List<Card> createDeck() {
        List<Card> deck = new ArrayList<>(52);
        for (int value = 1; value <= 13; value++) {
            for (int suit = 1; suit <= 4; suit++) {
                deck.add(new Card(value, suit));
            }
        }
        return deck;
    }

    private static final int DEFAULT_MC_TRIALS = 2000;

    public static double monteCarloEquity(Card playerCard1, Card playerCard2, List<Card> tableCards) {
        return monteCarloEquity(playerCard1, playerCard2, tableCards, 1, DEFAULT_MC_TRIALS);
    }

    public static double monteCarloEquity(Card playerCard1, Card playerCard2, List<Card> tableCards,
                                           int opponents, int trials) {
        if (tableCards == null) tableCards = Collections.emptyList();
        if (opponents < 1 || trials < 1) return 0;

        List<Card> known = new ArrayList<>();
        known.add(playerCard1);
        known.add(playerCard2);
        known.addAll(tableCards);

        List<Card> remainingDeck = createDeck();
        remainingDeck.removeAll(known);

        int cardsNeeded = (5 - tableCards.size()) + opponents * 2;
        if (cardsNeeded > remainingDeck.size()) {
            throw new IllegalArgumentException(
                "Not enough cards left in the deck for " + opponents + " opponents with this board.");
        }

        Random rng = new Random();
        double wins = 0;

        for (int t = 0; t < trials; t++) {
            Collections.shuffle(remainingDeck, rng);
            int next = 0;

            List<Card> board = new ArrayList<>(tableCards);
            while (board.size() < 5) {
                board.add(remainingDeck.get(next++));
            }

            List<Card> myCards = new ArrayList<>();
            myCards.add(playerCard1);
            myCards.add(playerCard2);
            myCards.addAll(board);
            HandResult myHand = bestFiveCardHand(myCards);

            boolean beaten = false;
            int tiedWithMe = 0;
            for (int o = 0; o < opponents; o++) {
                List<Card> oppCards = new ArrayList<>();
                oppCards.add(remainingDeck.get(next++));
                oppCards.add(remainingDeck.get(next++));
                oppCards.addAll(board);
                HandResult oppHand = bestFiveCardHand(oppCards);

                int cmp = compareHandResults(myHand, oppHand);
                if (cmp < 0) {
                    beaten = true;
                    break;
                } else if (cmp == 0) {
                    tiedWithMe++;
                }
            }

            if (!beaten) {
                wins += 1.0 / (1 + tiedWithMe);
            }
        }

        return clamp((wins / trials) * SCALE_MAX);
    }

    public static double cardStrength(Card playerCard1, Card playerCard2, List<Card> tableCards) {
        if (tableCards == null) tableCards = Collections.emptyList();

        if (tableCards.size() < 3) {

            return preflopScore(playerCard1, playerCard2);
        }

        List<Card> allCards = new ArrayList<>();
        allCards.add(playerCard1);
        allCards.add(playerCard2);
        allCards.addAll(tableCards);

        HandResult best = bestFiveCardHand(allCards);
        double score = handResultToScore(best);

        if (tableCards.size() < 5) {
            score += drawBonus(allCards);
        }

        return clamp(score);
    }

    private static double preflopScore(Card c1, Card c2) {
        int r1 = c1.getRankHigh();
        int r2 = c2.getRankHigh();
        int hi = Math.max(r1, r2);
        int lo = Math.min(r1, r2);

        double raw;
        if (hi == 14) raw = 10;
        else if (hi == 13) raw = 8;
        else if (hi == 12) raw = 7;
        else if (hi == 11) raw = 6;
        else if (hi == 10) raw = 5;
        else raw = hi / 2.0;

        if (r1 == r2) {
            raw = Math.max(5, raw * 2);
        }

        if (c1.getSuit() == c2.getSuit()) {
            raw += 2;
        }

        int gap = hi - lo - 1;
        if (r1 != r2) {
            if (gap == 0) raw += 1;
            else if (gap == 1) raw -= 1;
            else if (gap == 2) raw -= 2;
            else if (gap == 3) raw -= 4;
            else if (gap >= 4) raw -= 5;
        }

        raw = Math.max(0, Math.min(20, raw));
        return raw * (SCALE_MAX / 20.0);
    }

    private static final int HIGH_CARD = 0;
    private static final int ONE_PAIR = 1;
    private static final int TWO_PAIR = 2;
    private static final int TRIPS = 3;
    private static final int STRAIGHT = 4;
    private static final int FLUSH = 5;
    private static final int FULL_HOUSE = 6;
    private static final int QUADS = 7;
    private static final int STRAIGHT_FLUSH = 8;

    private static final double[][] BANDS = {
        {0, 20},
        {20, 38},
        {38, 50},
        {50, 60},
        {60, 70},
        {70, 80},
        {80, 90},
        {90, 97},
        {97, 100},
    };

    private static double handResultToScore(HandResult hand) {
        double[] band = BANDS[hand.category];
        double fraction = kickerFraction(hand);
        return band[0] + (band[1] - band[0]) * fraction;
    }

    private static double kickerFraction(HandResult hand) {

        double weighted = 0;
        double maxWeighted = 0;
        double weight = 1.0;
        for (int i = 0; i < hand.tiebreak.size(); i++) {
            weighted += hand.tiebreak.get(i) * weight;
            maxWeighted += 14 * weight;
            weight *= 0.2;
        }
        if (maxWeighted == 0) return 0;
        return weighted / maxWeighted;
    }

    private static HandResult bestFiveCardHand(List<Card> cards) {
        HandResult best = null;
        int n = cards.size();
        int[] idx = {0, 1, 2, 3, 4};
        List<int[]> combos = new ArrayList<>();
        combinations(n, 5, combos);
        for (int[] combo : combos) {
            List<Card> five = new ArrayList<>(5);
            for (int i : combo) five.add(cards.get(i));
            HandResult r = evaluateFive(five);
            if (best == null || compareHandResults(r, best) > 0) {
                best = r;
            }
        }
        return best;
    }

    private static void combinations(int n, int k, List<int[]> out) {
        int[] combo = new int[k];
        combineHelper(0, 0, n, k, combo, out);
    }

    private static void combineHelper(int start, int depth, int n, int k, int[] combo, List<int[]> out) {
        if (depth == k) {
            out.add(combo.clone());
            return;
        }
        for (int i = start; i < n; i++) {
            combo[depth] = i;
            combineHelper(i + 1, depth + 1, n, k, combo, out);
        }
    }

    private static int compareHandResults(HandResult a, HandResult b) {
        if (a.category != b.category) return Integer.compare(a.category, b.category);
        int len = Math.min(a.tiebreak.size(), b.tiebreak.size());
        for (int i = 0; i < len; i++) {
            int cmp = Integer.compare(a.tiebreak.get(i), b.tiebreak.get(i));
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    private static final String[] HAND_CATEGORY_NAMES = {
        "High card", "Pair", "Two pair", "Three of a kind", "Straight",
        "Flush", "Full house", "Four of a kind", "Straight flush"
    };

    public static int compareBestHands(List<Card> playerACards, List<Card> playerBCards) {
        HandResult a = bestFiveCardHand(playerACards);
        HandResult b = bestFiveCardHand(playerBCards);
        return compareHandResults(a, b);
    }

    public static String describeBestHand(List<Card> cards) {
        return HAND_CATEGORY_NAMES[bestFiveCardHand(cards).category];
    }

    private static HandResult evaluateFive(List<Card> five) {
        int[] rankCounts = new int[15];
        int[] suitCounts = new int[5];
        for (Card c : five) {
            rankCounts[c.getRankHigh()]++;
            suitCounts[c.getSuit()]++;
        }

        boolean isFlush = false;
        for (int s = 1; s <= 4; s++) {
            if (suitCounts[s] == 5) isFlush = true;
        }

        List<Integer> distinctRanksDesc = new ArrayList<>();
        for (int r = 14; r >= 2; r--) {
            if (rankCounts[r] > 0) distinctRanksDesc.add(r);
        }

        int straightHigh = straightHighCard(distinctRanksDesc);
        boolean isStraight = straightHigh > 0;

        List<Integer> ranksByCountThenValue = new ArrayList<>();
        for (int r = 14; r >= 2; r--) {
            if (rankCounts[r] > 0) ranksByCountThenValue.add(r);
        }
        ranksByCountThenValue.sort((x, y) -> {
            int cmp = Integer.compare(rankCounts[y], rankCounts[x]);
            if (cmp != 0) return cmp;
            return Integer.compare(y, x);
        });

        int topCount = rankCounts[ranksByCountThenValue.get(0)];
        int secondCount = ranksByCountThenValue.size() > 1 ? rankCounts[ranksByCountThenValue.get(1)] : 0;

        List<Integer> tiebreak = new ArrayList<>();
        int category;

        if (isStraight && isFlush) {
            category = STRAIGHT_FLUSH;
            tiebreak.add(straightHigh);
        } else if (topCount == 4) {
            category = QUADS;
            tiebreak.add(ranksByCountThenValue.get(0));
            tiebreak.add(ranksByCountThenValue.get(1));
        } else if (topCount == 3 && secondCount >= 2) {
            category = FULL_HOUSE;
            tiebreak.add(ranksByCountThenValue.get(0));
            tiebreak.add(ranksByCountThenValue.get(1));
        } else if (isFlush) {
            category = FLUSH;
            tiebreak.addAll(distinctRanksDesc.subList(0, Math.min(5, distinctRanksDesc.size())));
        } else if (isStraight) {
            category = STRAIGHT;
            tiebreak.add(straightHigh);
        } else if (topCount == 3) {
            category = TRIPS;
            tiebreak.add(ranksByCountThenValue.get(0));
            tiebreak.add(ranksByCountThenValue.get(1));
            tiebreak.add(ranksByCountThenValue.get(2));
        } else if (topCount == 2 && secondCount == 2) {
            category = TWO_PAIR;
            tiebreak.add(ranksByCountThenValue.get(0));
            tiebreak.add(ranksByCountThenValue.get(1));
            tiebreak.add(ranksByCountThenValue.get(2));
        } else if (topCount == 2) {
            category = ONE_PAIR;
            tiebreak.add(ranksByCountThenValue.get(0));
            tiebreak.add(ranksByCountThenValue.get(1));
            tiebreak.add(ranksByCountThenValue.get(2));
            tiebreak.add(ranksByCountThenValue.get(3));
        } else {
            category = HIGH_CARD;
            tiebreak.addAll(distinctRanksDesc.subList(0, Math.min(5, distinctRanksDesc.size())));
        }

        return new HandResult(category, tiebreak);
    }

    private static int straightHighCard(List<Integer> distinctRanksDesc) {
        Set<Integer> ranks = new HashSet<>(distinctRanksDesc);

        if (ranks.contains(14) && ranks.contains(2) && ranks.contains(3)
                && ranks.contains(4) && ranks.contains(5)) {

        }
        for (int high = 14; high >= 6; high--) {
            boolean all = true;
            for (int r = high; r > high - 5; r--) {
                if (!ranks.contains(r)) { all = false; break; }
            }
            if (all) return high;
        }
        if (ranks.contains(14) && ranks.contains(2) && ranks.contains(3)
                && ranks.contains(4) && ranks.contains(5)) {
            return 5;
        }
        return 0;
    }

    private static double drawBonus(List<Card> cards) {
        double bonus = 0;

        int[] suitCounts = new int[5];
        for (Card c : cards) suitCounts[c.getSuit()]++;
        for (int s = 1; s <= 4; s++) {
            if (suitCounts[s] == 4) bonus += 6;
        }

        Set<Integer> ranks = new HashSet<>();
        for (Card c : cards) ranks.add(c.getRankHigh());
        if (ranks.contains(14)) ranks.add(1);

        int bestOpenEnded = 0;
        int bestGutshot = 0;
        for (int high = 14; high >= 5; high--) {
            int have = 0;
            for (int r = high; r > high - 4; r--) {
                if (ranks.contains(r)) have++;
            }
            if (have == 4) bestOpenEnded = Math.max(bestOpenEnded, 1);
        }
        for (int high = 14; high >= 5; high--) {
            int have = 0;
            for (int r = high; r > high - 5; r--) {
                if (ranks.contains(r)) have++;
            }
            if (have == 4) bestGutshot = Math.max(bestGutshot, 1);
        }
        if (bestOpenEnded > 0) bonus += 5;
        else if (bestGutshot > 0) bonus += 3;

        return bonus;
    }

    private static double clamp(double score) {
        return Math.max(0, Math.min(SCALE_MAX, score));
    }

    private static class HandResult {
        final int category;
        final List<Integer> tiebreak;

        HandResult(int category, List<Integer> tiebreak) {
            this.category = category;
            this.tiebreak = tiebreak;
        }
    }

    public static double boardStrength(List<Card> tableCards) {
        if (tableCards == null || tableCards.isEmpty()) return 0;

        int[] rankCounts = new int[15];
        int[] suitCounts = new int[5];
        int highest = 0;
        for (Card c : tableCards) {
            int r = c.getRankHigh();
            rankCounts[r]++;
            suitCounts[c.getSuit()]++;
            highest = Math.max(highest, r);
        }

        double score = 0;

        score += (highest - 2) / 12.0 * 15;

        int maxOfAKind = 0;
        int pairCount = 0;
        for (int r = 2; r <= 14; r++) {
            if (rankCounts[r] > maxOfAKind) maxOfAKind = rankCounts[r];
            if (rankCounts[r] == 2) pairCount++;
        }
        if (maxOfAKind == 4) score += 45;
        else if (maxOfAKind == 3) score += 30;
        else if (maxOfAKind == 2 && pairCount >= 2) score += 22;
        else if (maxOfAKind == 2) score += 15;

        int maxSuit = 0;
        for (int s = 1; s <= 4; s++) maxSuit = Math.max(maxSuit, suitCounts[s]);
        if (maxSuit >= 5) score += 25;
        else if (maxSuit == 4) score += 20;
        else if (maxSuit == 3) score += 12;

        List<Integer> distinct = new ArrayList<>();
        for (int r = 14; r >= 2; r--) if (rankCounts[r] > 0) distinct.add(r);
        int straightHigh = straightHighCard(distinct);
        int span = distinct.isEmpty() ? 0 : distinct.get(0) - distinct.get(distinct.size() - 1);
        if (straightHigh > 0) score += 20;
        else if (distinct.size() >= 3 && span <= 4) score += 12;
        else if (distinct.size() >= 3 && span <= 6) score += 6;

        return clamp(score);
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