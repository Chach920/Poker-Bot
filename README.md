1. Goal
    I built this algorthm for fun. I've been playing poker with friends lately, and wanted to build something that used that experience and taught me a bit about statistics, algorithm design, etc...
    What kind of incited this project directly was my friend's flawed poker strategy. He would justify checks by comparing the cost of the check to the amount in the pot. Ex, if the pot was 100 and two people were playing, he would match a raise of 50 (total pot 200) if he thought he had a 25% chance of winning. However, he completely ignored the future raises, trapping him in a cycle that drained the winnings from his otherwise solid intuition, strategy, and luck.

2. General idea
    * Give hands and boards scoring based on potential to form a positive hand
    * Use collected data from prior IRL games to predict how the betting would continue into the game, allowing for better decision making when checking/raising/folding
    * Use differential between board score and hand scores to determine aggression

