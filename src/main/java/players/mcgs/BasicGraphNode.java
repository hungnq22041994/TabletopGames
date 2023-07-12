package players.mcgs;

import core.AbstractGameState;
import core.actions.AbstractAction;
import players.simple.RandomPlayer;

import java.util.*;

import static utilities.Utils.noise;

class BasicGraphNode {

    // State in this node
    protected AbstractGameState state;

    protected Map<AbstractAction, ActionStats> actionStatsMap = new HashMap<>();

    // Parameters guiding the search
    protected BasicMCGSPlayer player;
    protected Random rnd;
    protected RandomPlayer randomPlayer = new RandomPlayer();

    protected BasicGraphNode(BasicMCGSPlayer player, AbstractGameState state, Random rnd) {
        this.player = player;
        this.state = state.copy();
        this.rnd = rnd;
    }

    /**
     * Advance the current game state with the given action, count the FM call and compute the next available actions.
     *
     * @param gs  - current game state
     * @param act - action to apply
     */
    protected void advance(AbstractGameState gs, AbstractAction act) {
        player.getForwardModel().next(gs, act);
    }

    protected AbstractAction ucb(List<AbstractAction> availableActions) {
        // Find child with highest UCB value, maximising for ourselves and minimizing for opponent
        AbstractAction bestAction = null;
        double bestValue = -Double.MAX_VALUE;
        int totalVisit = getTotalVisit();

        for (AbstractAction action: availableActions) {
            ActionStats actionStats = actionStatsMap.get(action);
            if (Objects.isNull(actionStats)) {
                throw new RuntimeException("Should not be here");
            }
            // Find child value
            double hvVal = actionStats.totValue;
            double childValue = hvVal / (actionStats.nVisits + player.params.epsilon);

            // default to standard UCB
            double explorationTerm = player.params.K * Math.sqrt(Math.log(totalVisit + 1) / (actionStats.nVisits + player.params.epsilon));
            // unless we are using a variant

            // Find 'UCB' value
            // If 'we' are taking a turn we use classic UCB
            // If it is an opponent's turn, then we assume they are trying to minimise our score (with exploration)
            boolean iAmMoving = state.getCurrentPlayer() == player.getPlayerID();
            double uctValue = iAmMoving ? childValue : -childValue;
            uctValue += explorationTerm;

            // Apply small noise to break ties randomly
            uctValue = noise(uctValue, player.params.epsilon, player.rnd.nextDouble());

            // Assign value
            if (uctValue > bestValue) {
                bestAction = action;
                bestValue = uctValue;
            }
        }

        if (bestAction == null)
            throw new AssertionError("We have a null value in UCT : shouldn't really happen!");

        return bestAction;
    }

    /**
     * Perform a Monte Carlo rollout from this node.
     *
     * @return - value of rollout.
     */
    protected double rollOut() {
        int rolloutDepth = 0; // counting from end of tree

        // If rollouts are enabled, select actions for the rollout in line with the rollout policy
        AbstractGameState rolloutState = state.copy();
        if (player.params.rolloutLength > 0) {
            while (!finishRollout(rolloutState, rolloutDepth)) {
                List<AbstractAction> availableActions = player.getForwardModel().computeAvailableActions(rolloutState);
                AbstractAction next = randomPlayer.getAction(rolloutState, availableActions);
                advance(rolloutState, next);
                rolloutDepth++;
            }
        }
        // Evaluate final state and return normalised score
        double value = player.params.getHeuristic().evaluateState(rolloutState, player.getPlayerID());
        if (Double.isNaN(value))
            throw new AssertionError("Illegal heuristic value - should be a number");
        return value;
    }

    /**
     * Checks if rollout is finished. Rollouts end on maximum length, or if game ended.
     *
     * @param rollerState - current state
     * @param depth       - current depth
     * @return - true if rollout finished, false otherwise
     */
    private boolean finishRollout(AbstractGameState rollerState, int depth) {
        if (depth >= player.params.rolloutLength)
            return true;

        // End of game
        return !rollerState.isNotTerminal();
    }


    /**
     * Calculates the best action from the root according to the most visited node
     *
     * @return - the best AbstractAction
     */
    protected AbstractAction bestAction() {

        double bestValue = -Double.MAX_VALUE;
        AbstractAction bestAction = null;

        for (Map.Entry<AbstractAction, ActionStats> action : actionStatsMap.entrySet()) {
            double childValue = action.getValue().nVisits;

            // Apply small noise to break ties randomly
            childValue = noise(childValue, player.params.epsilon, player.rnd.nextDouble());

            // Save best value (the highest visit count)
            if (childValue > bestValue) {
                bestValue = childValue;
                bestAction = action.getKey();
            }
        }

        if (bestAction == null) {
            throw new AssertionError("Unexpected - no selection made.");
        }

        return bestAction;
    }

    protected int getTotalVisit() {
        int count = 0;
        for (Map.Entry<AbstractAction, ActionStats> entry: actionStatsMap.entrySet()) {
            count += entry.getValue().getnVisits();
        }
        return count;
    }

}
