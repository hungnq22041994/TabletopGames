package players.mcgs;

import core.AbstractGameState;
import core.actions.AbstractAction;
import org.jetbrains.annotations.NotNull;
import players.PlayerConstants;
import utilities.ElapsedCpuTimer;
import utilities.Pair;

import java.util.*;

import static players.PlayerConstants.*;

public class BasicGraph {

    private final Map<String, BasicGraphNode> transpositionMap = new HashMap<>();
    private final BasicGraphNode rootNode;

    protected BasicMCGSPlayer player;

    protected Random rnd;

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

        Deque<Pair<BasicGraphNode, AbstractAction>> trajectories = new ArrayDeque<>();
        while (!stop) {
            // New timer for this iteration
            ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();

            // Selection + expansion: navigate tree until a node not fully expanded is found, add a new node to the tree
            BasicGraphNode selected = graphPolicy(rootNode, trajectories);

            // Monte carlo rollout: return value of MC rollout from the newly added node
            double delta = selected.rollOut();
            // Back up the value of the rollout through the tree
            backUp(delta, trajectories);
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

    /**
     * Selection + expansion steps.
     *
     */

    protected BasicGraphNode graphPolicy(BasicGraphNode node, Deque<Pair<BasicGraphNode, AbstractAction>> trajectories) {
        BasicGraphNode currentNode = node;
        AbstractGameState nextState = node.state.copy();

        while (currentNode.state.isNotTerminal()) {
            List<AbstractAction> availableActions = currentNode.player.getForwardModel().computeAvailableActions(currentNode.state);
            populateNodeActionStats(currentNode, availableActions);

            List<AbstractAction> unexpandedActions = getUnexpandedActions(currentNode);

            if (!unexpandedActions.isEmpty()) {
                Random r = new Random();
                AbstractAction action = unexpandedActions.get(r.nextInt(unexpandedActions.size()));
                trajectories.push(new Pair<>(currentNode, action));
                currentNode.advance(nextState, action);
                String hashCode = getHashCode(nextState, currentNode.player.getPlayerID());
                if (isNodeExist(hashCode)) {
                    return getNode(hashCode);
                } else {
                    return createNewNode(nextState, hashCode);
                }
            } else {
                AbstractAction action = currentNode.ucb(availableActions);
                trajectories.push(new Pair<>(currentNode, action));
                currentNode.advance(nextState, action);
                String hashCode = getHashCode(nextState, currentNode.player.getPlayerID());
                if (isNodeExist(hashCode)) {
                    currentNode = getNode(hashCode);
                } else {
                    currentNode = createNewNode(nextState, hashCode);
                }
            }
        }

        return currentNode;
    }


    private BasicGraphNode createNewNode(AbstractGameState nextState, String hashCode) {
        BasicGraphNode new_node = new BasicGraphNode(player, nextState, rnd);
        putNode(hashCode, new_node);
        return new_node;
    }

    private static List<AbstractAction> getUnexpandedActions(BasicGraphNode currentNode) {
        List<AbstractAction> unexpandedActions = new ArrayList<>();
        for (Map.Entry<AbstractAction, ActionStats> entry: currentNode.actionStatsMap.entrySet()) {
            if (entry.getValue().nVisits == 0) {
                unexpandedActions.add(entry.getKey());
            }
        }
        return unexpandedActions;
    }

    private static void populateNodeActionStats(BasicGraphNode currentNode, List<AbstractAction> availableActions) {
        for (AbstractAction action : availableActions) {
            if (!currentNode.actionStatsMap.containsKey(action)) {
                ActionStats actionStats = new ActionStats();
                currentNode.actionStatsMap.put(action, actionStats);
            }
        }
    }


    public void putNode(String hashCode, BasicGraphNode node) {
        if (!isNodeExist(hashCode)) {
            transpositionMap.put(hashCode, node);
        }
    }

    public BasicGraphNode putIfAbsent(String hashCode, BasicGraphNode node) {
        if (!isNodeExist(hashCode)) {
            return transpositionMap.put(hashCode, node);
        } else {
            return transpositionMap.get(hashCode);
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
     * Back up the value of the child through all parents. Increase number of visits and total value.
     *
     * @param result         - value of rollout to back up
     * @param trajectories
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
}
