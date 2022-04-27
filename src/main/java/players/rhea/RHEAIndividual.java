package players.rhea;

import core.AbstractForwardModel;
import core.AbstractGameState;
import core.actions.AbstractAction;
import core.interfaces.IStateHeuristic;
import players.rmhc.Individual;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class RHEAIndividual implements Comparable {

    AbstractAction[] actions;         // Actions in individual. Intended max length of individual = actions.length
    AbstractGameState[] gameStates;   // Game states in individual.
    double value;                     // Fitness of individual, to be maximised.
    int length;                       // Actual length of individual, <= actions.length
    double discountFactor;            // Discount factor for calculating rewards

    private Random gen;               // Random generator
    IStateHeuristic heuristic;

    RHEAIndividual(int L, double discountFactor, AbstractForwardModel fm, AbstractGameState gs, int playerID, Random gen, IStateHeuristic heuristic) {
        // Initialize
        this.gen = gen;
        this.discountFactor = discountFactor;
        actions = new AbstractAction[L];
        gameStates = new AbstractGameState[L+1];
        gameStates[0] = gs.copy();
        this.heuristic = heuristic;

        // Rollout with random actions and assign fitness value
        rollout(gs, fm, 0, L, playerID);  // TODO: cheating, init should also count FM calls
    }

    // Copy constructor
    RHEAIndividual(RHEAIndividual I){
        actions = new AbstractAction[I.actions.length];
        gameStates = new AbstractGameState[I.gameStates.length];
        length = I.length;
        discountFactor = I.discountFactor;

        for (int i = 0; i < length; i++){
            actions[i] = I.actions[i].copy();
            gameStates[i] = I.gameStates[i].copy();
        }

        value = I.value;
        gen = I.gen;
    }

    /**
     * Mutates this individual, by picking an index and changing all genes from that point on.
     * Updates the length of the individual in case the rollout hits game end.
     * Also evaluates the individual as a rollout is needed for mutation, and updates the value.
     * @param fm - forward model
     * @param playerID - ID of player, used in evaluation of fitness
     * @return number of calls to the FM.next() function, as the difference between length after rollout and start
     *          index of rollout
     */
    public int mutate(AbstractForwardModel fm, int playerID){
        if (length > 0) {
            // Find index from which to mutate individual, random in range of currently valid length
            int startIndex = 0;
            if (length > 1) {
                startIndex = gen.nextInt(length - 1);
            }
            // Last index is maximum intended individual length
            int endIndex = actions.length;
            // Game state to start from
            AbstractGameState gs = gameStates[startIndex];
            // Perform rollout and return number of FM calls taken. Always rollout from 0 due to preceeding crossover changing previous actions.
            return rollout(gs, fm, startIndex, endIndex, playerID);
        }
        return 0;
    }

    public int repair(AbstractForwardModel fm)
    {
        int fmCalls = 0;
        for(int i = 0; i < length; ++i)
        {
            List<AbstractAction> currentActions = fm.computeAvailableActions(gameStates[i]);
            if(!currentActions.contains(actions[i]))
                actions[i] = currentActions.get(gen.nextInt(currentActions.size()));
            fm.next(gameStates[i + 1], actions[i]);
            fmCalls++;
        }
        return fmCalls;
    }

    private double evaluate(int playerID)
    {
        double delta = 0;
        for (int i = 0; i < length; i++) {
            double score;
            if (this.heuristic != null){
                score = heuristic.evaluateState(gameStates[i+1], playerID);
            } else {
                score = gameStates[i+1].getGameScore(playerID);
            }

            delta += Math.pow(discountFactor, i) * score;
        }
        return delta;
    }

    /**
     * Performs a rollout with random actions from startIndex to endIndex in the individual, from root game state gs.
     * Evaluates the final state reached and returns the number of calls to the FM.next() function.
     * @param gs - root game state from which to start rollout
     * @param fm - forward model
     * @param startIndex - index in individual from which to start rollout
     * @param endIndex - index in individual where to end rollout
     * @param playerID - ID of player, used in state evaluation
     * @return - number of calls to the FM.next() function
     */
    public int rollout(AbstractGameState gs, AbstractForwardModel fm, int startIndex, int endIndex, int playerID) {
        length = 0;
        int fmCalls = repair(fm);
        double delta = 0;

        for (int i = startIndex; i < endIndex; i++){
            // Rolls from chosen index to the end, randomly changing actions and game states
            // Length of individual is updated depending on if it reaches a terminal game state
            if (gs.isNotTerminal()) {
                // Copy the game state
                AbstractGameState gsCopy = gs.copy();
                List<AbstractAction> currentActions = fm.computeAvailableActions(gsCopy);
                AbstractAction action = null;
                if (currentActions.size() > 0) {
                    action = currentActions.get(gen.nextInt(currentActions.size()));
                }

                // Advance game state with random action
                fm.next(gsCopy, action);
                fmCalls ++;

                // If it's my turn, store this in the individual
                boolean iAmMoving = (gameStates[i].getCurrentPlayer() == playerID);
                if (iAmMoving) {
                    gameStates[i + 1] = gsCopy;
                    actions[i] = action;

                    // Individual length increased
                    length++;
                } else {
                    i--;
                }

                gs = gsCopy;
            } else {
                break;
            }
        }
//        this.value = gs.getScore(playerID);
        this.value = evaluate(playerID);
        return fmCalls;
    }

    @Override
    public int compareTo(Object o) {
        RHEAIndividual a = this;
        RHEAIndividual b = (RHEAIndividual)o;
        return Double.compare(b.value, a.value);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Individual)) return false;

        RHEAIndividual a = this;
        RHEAIndividual b = (RHEAIndividual)o;

        for (int i = 0; i < actions.length; i++) {
            if (!a.actions[i].equals(b.actions[i])) return false;
        }

        return true;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder("" + value + ": ");
        for (AbstractAction action : actions) s.append(action).append(" ");
        return s.toString();
    }
}
