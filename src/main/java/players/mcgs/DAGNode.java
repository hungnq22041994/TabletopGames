package players.mcgs;

import core.AbstractGameState;
import core.actions.AbstractAction;
import players.simple.RandomPlayer;

import java.util.List;
import java.util.Random;

public class DAGNode {

    // State in this node
    protected AbstractGameState state;

    // Parameters guiding the search
    protected MCGSPlayer player;
    protected Random rnd;
    boolean isTerminal;
    protected RandomPlayer randomPlayer = new RandomPlayer();

    protected DAGNode(MCGSPlayer player, AbstractGameState state, Random rnd) {
        this.player = player;
        this.state = state.copy();
        this.rnd = rnd;
        this.isTerminal = !state.isNotTerminal();
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

}
