package players.mcgs;

import core.AbstractGameState;
import core.actions.AbstractAction;
import players.PlayerConstants;
import utilities.ElapsedCpuTimer;
import utilities.Pair;

import java.util.*;

import static players.PlayerConstants.*;

public class BasicGraph {
    //    State S -> action a -> hash the State(s,a) -> put in transpositionMap, hash value ,
    //    if we advance from a random state, with action -> look in to the transpositionMap to find the state
    //    tic tac toe -> several hash version, get the game state (position of x and o)
    //    hash function H(s)
    private final Map<String, BasicGraphNode> transpositionMap = new HashMap<>();
    private final BasicGraphNode rootNode;

    // Number of FM calls and State copies for this graph
    private int fmCallsCount;

    protected BasicMCGSPlayer player;

    protected Random rnd;

    public BasicGraph(BasicMCGSPlayer player, AbstractGameState state, Random rnd) {
        this.player = player;
        this.rnd = rnd;
        this.rootNode = new BasicGraphNode(player, state, rnd);
        transpositionMap.put(getHashCode(state, player.getPlayerID()), this.rootNode);
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

//        node + action
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
            } else if (budgetType == BUDGET_FM_CALLS) {
                // FM calls budget
                stop = getFmCallsCount() > player.params.budget;
            }
        }
    }

    /**
     * Selection + expansion steps.
     *
     */

    private BasicGraphNode graphPolicy(BasicGraphNode node, Deque<Pair<BasicGraphNode, AbstractAction>> trajectories) {
        BasicGraphNode currentNode = node;

        while (currentNode.state.isNotTerminal()) {
            List<AbstractAction> availableActions = currentNode.player.getForwardModel().computeAvailableActions(currentNode.state);
            for (AbstractAction action : availableActions) {
                if (!currentNode.actionStatsMap.containsKey(action)) {
                    ActionStats actionStats = new ActionStats();
                    currentNode.actionStatsMap.put(action, actionStats);
                }
            }

            List<AbstractAction> unexpandedActions = new ArrayList<>();
            for (Map.Entry<AbstractAction, ActionStats> entry: currentNode.actionStatsMap.entrySet()) {
                if (entry.getValue().nVisits == 0) {
                    unexpandedActions.add(entry.getKey());
                }
            }

            if (!unexpandedActions.isEmpty()) {
                Random r = new Random();
                AbstractAction action = unexpandedActions.get(r.nextInt(unexpandedActions.size()));
                trajectories.push(new Pair<>(currentNode, action));
                AbstractGameState nextState = currentNode.state.copy();
                currentNode.advance(nextState, action);
                String hashCode = getHashCode(nextState, currentNode.player.getPlayerID());
                if (isNodeExist(hashCode)) {
                    return getNode(hashCode);
                } else {
                    BasicGraphNode new_node = new BasicGraphNode(player, nextState, rnd);
                    putNode(hashCode, new_node);
                    currentNode = new_node;
                    return currentNode;
                }
            } else {
//                todo: reduce state copy
                AbstractAction action = currentNode.ucb(availableActions);
                trajectories.push(new Pair<>(currentNode, action));
                AbstractGameState nextState = currentNode.state.copy();
                currentNode.advance(nextState, action);
                String hashCode = getHashCode(nextState, currentNode.player.getPlayerID());
                if (isNodeExist(hashCode)) {
                    currentNode = getNode(hashCode);
                } else {
                    BasicGraphNode new_node = new BasicGraphNode(player, nextState, rnd);
                    putNode(hashCode, new_node);
                    currentNode = new_node;
                }
            }
        }

        return currentNode;
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

    public void increaseFmCallCount() {
        fmCallsCount++;
    }

    public int getFmCallsCount() {
        return fmCallsCount;
    }

    public void setFmCallsCount(int fmCallsCount) {
        this.fmCallsCount = fmCallsCount;
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

    protected int getHashCode(BasicGraphNode node) {
        double[] a = player.params.EIStateFeatureVector.featureVector(node.state, node.player.getPlayerID());
        return Arrays.hashCode(a);
    }

    private String getHashCode(AbstractGameState state, int playerID) {
        double[] featureVector = player.params.EIStateFeatureVector.featureVector(state, playerID);
        return Arrays.toString(featureVector);
    }
}
