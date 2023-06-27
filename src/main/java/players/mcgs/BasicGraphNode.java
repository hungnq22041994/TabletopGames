package players.mcgs;

import core.AbstractGameState;
import core.actions.AbstractAction;
import players.PlayerConstants;
import players.simple.RandomPlayer;
import utilities.ElapsedCpuTimer;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static players.PlayerConstants.*;
import static utilities.Utils.noise;

class BasicGraphNode {
    BasicGraph basicGraph;

    // State in this node (open loop - this is updated by onward trajectory....be very careful about using)
    protected AbstractGameState openLoopState;

    protected AbstractGameState state;

//    applied the action already, have i took this action already
//    store statistic for ucb
    private Map<AbstractAction, ActionStats> actionMap = new HashMap<>();

    // Parameters guiding the search
    private BasicMCGSPlayer player;
    private Random rnd;
    private RandomPlayer randomPlayer = new RandomPlayer();

    protected BasicGraphNode(BasicMCGSPlayer player, BasicGraphNode parent, BasicGraph basicGraph, AbstractGameState state, Random rnd) {
        this.player = player;
        if (Objects.nonNull(basicGraph)) {
            this.basicGraph = basicGraph;
        } else {
            this.basicGraph = new BasicGraph();
        }
        setState(state);
        this.rnd = rnd;
    }

    /**
     * Performs full MCTS search, using the defined budget limits.
     */
    void mcgsSearch() {

        // Variables for tracking time budget
        double avgTimeTaken;
        double acumTimeTaken = 0;
        long remaining;
        int remainingLimit = player.params.breakMS;
        ElapsedCpuTimer elapsedTimer = new ElapsedCpuTimer();
        if (player.params.budgetType == BUDGET_TIME) {
            elapsedTimer.setMaxTimeMillis(player.params.budget);
        }

        // Tracking number of iterations for iteration budget
        int numIters = 0;

        boolean stop = false;

        while (!stop) {
            // New timer for this iteration
            ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();

            // Selection + expansion: navigate tree until a node not fully expanded is found, add a new node to the tree
            BasicGraphNode selected = treePolicy();
            // Monte carlo rollout: return value of MC rollout from the newly added node
            double delta = selected.rollOut();
            // Back up the value of the rollout through the tree
            selected.backUp(delta);
            // Finished iteration
            numIters++;

            // Check stopping condition
            PlayerConstants budgetType = player.params.budgetType;
            if (budgetType == BUDGET_TIME) {
                // Time budget
                acumTimeTaken += (elapsedTimerIteration.elapsedMillis());
                avgTimeTaken = acumTimeTaken / numIters;
                remaining = elapsedTimer.remainingTimeMillis();
                stop = remaining <= 2 * avgTimeTaken || remaining <= remainingLimit;
            } else if (budgetType == BUDGET_ITERATIONS) {
                // Iteration budget
                stop = numIters >= player.params.budget;
            } else if (budgetType == BUDGET_FM_CALLS) {
                // FM calls budget
                stop = basicGraph.getFmCallsCount() > player.params.budget;
            }
        }
    }

    /**
     * Selection + expansion steps.
     * - Tree is traversed until a node not fully expanded is found.
     * - A new child of this node is added to the tree.
     *
     * @return - new node added to the tree.
     */
    private BasicGraphNode treePolicy() {

        BasicGraphNode cur = this;

        // Keep iterating while the state reached is not terminal and the depth of the tree is not exceeded
        while (cur.state.isNotTerminal()) {
            if (!cur.unexpandedActions().isEmpty()) {
                // We have an unexpanded action
                cur = cur.expand();
                return cur;
            } else {
                // Move to next child given by UCT function
                AbstractAction actionChosen = cur.ucb();
                actionChosen.execute(openLoopState);
                int hashCode = getHashCode(openLoopState);
                if (basicGraph.isNodeExist(hashCode)) {
                    return basicGraph.getNode(hashCode);
                } else {
                    basicGraph.putNode(hashCode, this);
                }
            }
        }

        return cur;
    }

    private void setState(AbstractGameState newState) {
        this.state = newState.copy();
        this.openLoopState = newState.copy();
        for (AbstractAction action : player.getForwardModel().computeAvailableActions(state)) {
            actionMap.put(action, false);
        }
    }

    /**
     * @return A list of the unexpanded Actions from this State
     */
    private List<AbstractAction> unexpandedActions() {
        return actionMap.keySet().stream().filter(a -> !actionMap.get(a)).collect(toList());
    }

    /**
     * Expands the node by creating a new random child node and adding to the tree.
     *
     * @return - new child node.
     */

    private BasicGraphNode expand() {
        // Find random child not already created
        Random r = new Random(player.params.getRandomSeed());
        // pick a random not chosen action
        List<AbstractAction> notChosen = unexpandedActions();
        AbstractAction chosen = notChosen.get(r.nextInt(notChosen.size()));

        // copy the current state and advance it using the chosen action
        // we first copy the action so that the one stored in the node will not have any state changes
        AbstractGameState nextState = openLoopState.copy();
        advance(nextState, chosen.copy());

        // then instantiate a new node or get existing node from transposition table
        actionMap.put(chosen, true);

        if (basicGraph.isNodeExist(getHashCode(nextState))) {
            return basicGraph.getNode(getHashCode(nextState));
        } else {
            BasicGraphNode basicGraphNode = new BasicGraphNode(player, this, basicGraph, nextState, rnd);
            basicGraph.putNode(getHashCode(nextState), basicGraphNode);
            return basicGraphNode;
        }
    }

    /**
     * Advance the current game state with the given action, count the FM call and compute the next available actions.
     *
     * @param gs  - current game state
     * @param act - action to apply
     */
    private void advance(AbstractGameState gs, AbstractAction act) {
        player.getForwardModel().next(gs, act);
        basicGraph.increaseFmCallCount();
    }

    private AbstractAction ucb() {
        // Find child with highest UCB value, maximising for ourselves and minimizing for opponent
        AbstractAction bestAction = null;
        double bestValue = -Double.MAX_VALUE;

        for (AbstractAction action : actionMap.keySet()) {
            AbstractGameState nextState = openLoopState.copy();
            advance(nextState, action);
            BasicGraphNode child = getNode(nextState);
            if (child == null)
                throw new AssertionError("Should not be here");
            else if (bestAction == null)
                bestAction = action;

            // Find child value
            double hvVal = child.totValue;
            double childValue = hvVal / (child.nVisits + player.params.epsilon);

            // default to standard UCB
            double explorationTerm = player.params.K * Math.sqrt(Math.log(this.nVisits + 1) / (child.nVisits + player.params.epsilon));
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

        basicGraph.increaseFmCallCount();
        return bestAction;
    }

    /**
     * Perform a Monte Carlo rollout from this node.
     *
     * @return - value of rollout.
     */
    private double rollOut() {
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
     * Back up the value of the child through all parents. Increase number of visits and total value.
     *
     * @param result - value of rollout to back up
     */
    private void backUp(double result) {
        BasicGraphNode n = this;
        while (n != null) {
            n.nVisits++;
            n.totValue += result;
//            n = n.parent;
//            if (n == this) {
//                break;
//            }
//            round or turn info
//            record the node and the action that led to this node in a list to back prop
//            stat store in the node that make decision
        }
    }

    /**
     * Calculates the best action from the root according to the most visited node
     *
     * @return - the best AbstractAction
     */
    AbstractAction bestAction() {

        double bestValue = -Double.MAX_VALUE;
        AbstractAction bestAction = null;

        for (AbstractAction action : actionMap.keySet()) {
            if (actionMap.get(action)) {

                AbstractGameState nextState = openLoopState.copy();
                advance(nextState, action);
                BasicGraphNode node = getNode(nextState);
                double childValue = node.nVisits;

                // Apply small noise to break ties randomly
                childValue = noise(childValue, player.params.epsilon, player.rnd.nextDouble());

                // Save best value (the highest visit count)
                if (childValue > bestValue) {
                    bestValue = childValue;
                    bestAction = action;
                }
            }
        }

        if (bestAction == null) {
            throw new AssertionError("Unexpected - no selection made.");
        }

        return bestAction;
    }

    private BasicGraphNode getNode(AbstractGameState nextState) {
        return basicGraph.getNode(getHashCode(nextState));
    }

    private int getHashCode(AbstractGameState nextState) {
        return Arrays.hashCode(player.params.EIStateFeatureVector.featureVector(nextState, player.getPlayerID()));
    }

}
