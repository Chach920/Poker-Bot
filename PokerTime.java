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