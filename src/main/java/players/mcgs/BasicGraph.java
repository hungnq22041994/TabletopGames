package players.mcgs;

import core.AbstractGameState;
import core.actions.AbstractAction;
import players.PlayerConstants;
import utilities.ElapsedCpuTimer;
import utilities.Pair;

import java.util.*;

import static players.PlayerConstants.BUDGET_ITERATIONS;
import static players.PlayerConstants.BUDGET_TIME;

public class BasicGraph {

    private final Map<String, BasicGraphNode> transpositionMap = new HashMap<>();
    private final BasicGraphNode rootNode;

    protected BasicMCGSPlayer player;

    protected Random rnd;

    public int depthReached;

    public BasicGraph(BasicMCGSPlayer player, AbstractGameState state, Random rnd) {
        this.player = player;
        this.rnd = rnd;
        this.rootNode = new BasicGraphNode(player, state, rnd);
        transpositionMap.put(getHashCode(state, player.getPlayerID()), this.rootNode);
    }

    public Map<String, BasicGraphNode> getTranspositionMap() {
        return transpositionMap;
    }

    public BasicGraphNode getRootNode() {
        return rootNode;
    }

    /**
     * Performs full MCGS search, using the defined budget limits.
     */
    void mcgsSearch() {

        // Variables for tracking time budget
        double avgTimeTaken;
        double acumTimeTaken = 0;
        long remaining;
        int remainingLimit = rootNode.player.params.breakMS;
        ElapsedCpuTimer elapsedTimer = new ElapsedCpuTimer();
        if (rootNode.player.params.budgetType == BUDGET_TIME) {
            elapsedTimer.setMaxTimeMillis(rootNode.player.params.budget);
        }

        // Tracking number of iterations for iteration budget
        int numIters = 0;

        boolean stop = false;

        while (!stop) {
            // New timer for this iteration
            ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();

            // Selection + expansion: navigate tree until a node not fully expanded is found, add a new node to the graph
            Pair<BasicGraphNode, Deque<Pair<BasicGraphNode, AbstractAction>>> policy = graphPolicy(rootNode);

            // Monte carlo rollout: return value of MC rollout from the newly added node
            double delta = policy.a.rollOut();
            getMaxDepth(policy);
            // Back up the value of the rollout through the trajectory
            backUp(delta, policy.b);
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
            }
        }
    }

    private void getMaxDepth(Pair<BasicGraphNode, Deque<Pair<BasicGraphNode, AbstractAction>>> policy) {
        int size = policy.b.size();
        if (depthReached < size) {
            depthReached = size;
        }
    }

    /**
     * Selection + expansion steps.
     */

    protected Pair<BasicGraphNode, Deque<Pair<BasicGraphNode, AbstractAction>>> graphPolicy(BasicGraphNode node) {
        Deque<Pair<BasicGraphNode, AbstractAction>> trajectories = new ArrayDeque<>();
        BasicGraphNode currentNode = node;
        AbstractGameState nextState = currentNode.state.copy();

        while (nextState.isNotTerminal()) {
            List<AbstractAction> availableActions = currentNode.player.getForwardModel().computeAvailableActions(nextState);

            List<AbstractAction> unexpandedActions = new ArrayList<>();
            for (AbstractAction action : availableActions) {
                if (!currentNode.actionStatsMap.containsKey(action)) {
                    ActionStats actionStats = new ActionStats();
                    currentNode.actionStatsMap.put(action, actionStats);
                    unexpandedActions.add(action);
                } else {
                    if (currentNode.actionStatsMap.get(action).getnVisits() == 0) {
                        unexpandedActions.add(action);
                    }
                }
            }

            if (!unexpandedActions.isEmpty()) {
                Random r = new Random();
                AbstractAction action = unexpandedActions.get(r.nextInt(unexpandedActions.size()));
                trajectories.push(new Pair<>(currentNode, action));
                currentNode.advance(nextState, action.copy());
                String hashCode = getHashCode(nextState, currentNode.player.getPlayerID());
                if (isNodeExist(hashCode)) {
                    return new Pair<>(getNode(hashCode), trajectories);
                } else {
                    return new Pair<>(createNewNode(nextState.copy(), hashCode), trajectories);
                }
            } else {
                AbstractAction action = currentNode.ucb(availableActions);
                trajectories.push(new Pair<>(currentNode, action));
                currentNode.advance(nextState, action.copy());
                String hashCode = getHashCode(nextState, currentNode.player.getPlayerID());
                if (isNodeExist(hashCode)) {
                    currentNode = getNode(hashCode);
                } else {
                    currentNode = createNewNode(nextState.copy(), hashCode);
                }
            }
        }

        return new Pair<>(currentNode, trajectories);
    }


    private BasicGraphNode createNewNode(AbstractGameState nextState, String hashCode) {
        BasicGraphNode new_node = new BasicGraphNode(player, nextState, rnd);
        putNode(hashCode, new_node);
        return new_node;
    }

    public void putNode(String hashCode, BasicGraphNode node) {
        if (!isNodeExist(hashCode)) {
            transpositionMap.put(hashCode, node);
        }
    }

    public boolean isNodeExist(String hashCode) {
        return Objects.nonNull(transpositionMap.get(hashCode));
    }

    public BasicGraphNode getNode(String hashCode) {
        return transpositionMap.get(hashCode);
    }

    public AbstractAction bestAction() {
        return rootNode.bestAction();
    }

    /**
     * Back up the value of the all nodes in trajectories. Increase number of visits and total value.
     *
     * @param result       - value of rollout to back up
     * @param trajectories - sequence of node + action
     */
    protected void backUp(double result, Deque<Pair<BasicGraphNode, AbstractAction>> trajectories) {
        while (trajectories.peekLast() != null) {
            Pair<BasicGraphNode, AbstractAction> trajectory = trajectories.removeLast();
            backupForAction(result, trajectory);
        }
    }

    private void backupForAction(double result, Pair<BasicGraphNode, AbstractAction> trajectory) {
        BasicGraphNode node = trajectory.a;
        if (Objects.nonNull(node)) {
            ActionStats actionStats = node.actionStatsMap.get(trajectory.b);
            if (Objects.nonNull(actionStats)) {
                actionStats.nVisits++;
                actionStats.totValue += result;
            } else {
                actionStats = new ActionStats(result, 1);
                node.actionStatsMap.put(trajectory.b, actionStats);
            }
        }
    }

    private String getHashCode(AbstractGameState state, int playerID) {
        double[] featureVector = player.params.EIStateFeatureVector.featureVector(state, playerID);
        return Arrays.toString(featureVector);
    }

    @Override
    public String toString() {
        StringBuilder retValue = new StringBuilder();
        retValue.append(new GraphStatistics(this));
        return retValue.toString();
    }
}
