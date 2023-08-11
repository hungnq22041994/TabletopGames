package players.mcgs;

import core.AbstractGameState;
import core.actions.AbstractAction;
import org.apache.commons.lang3.StringUtils;
import players.PlayerConstants;
import utilities.ElapsedCpuTimer;
import utilities.Pair;

import java.util.*;

import static players.PlayerConstants.BUDGET_ITERATIONS;
import static players.PlayerConstants.BUDGET_TIME;
import static utilities.Utils.noise;

public class DAG {
    private final Map<String, DAGNode> transpositionMap = new HashMap<>();
    private final Map<String, Map<AbstractAction, Edge>> adjacencyList = new HashMap<>();

    private DAGNode rootNode;

    protected MCGSPlayer player;

    protected Random rnd;

    public DAG(MCGSPlayer player, AbstractGameState state, Random rnd) {
        this.player = player;
        this.rnd = rnd;
        this.rootNode = new DAGNode(player, state, rnd);
        this.addVertex(rootNode);
        transpositionMap.put(getHashCode(state, player.getPlayerID()), this.rootNode);
    }

    public Map<String, DAGNode> getTranspositionMap() {
        return transpositionMap;
    }

    public DAGNode getRootNode() {
        return rootNode;
    }

    // Add a new vertex to the DAG
    public void addVertex(DAGNode vertex) {
        String hashCode = getHashCode(vertex.state, vertex.player.getPlayerID());
        adjacencyList.putIfAbsent(hashCode, new HashMap<>());
    }

    // Add a new vertex to the DAG
    public void addVertex(String hashCode) {
        adjacencyList.putIfAbsent(hashCode, new HashMap<>());
    }

    public Map<AbstractAction, Edge> getEdges(DAGNode vertex) {
        String hashCode = getHashCode(vertex.state, vertex.player.getPlayerID());
        return Optional.of(adjacencyList.get(hashCode)).orElse(new HashMap<>());
    }

    public Map<AbstractAction, Edge> getEdges(String hashCode) {
        return Optional.of(adjacencyList.get(hashCode)).orElse(new HashMap<>());
    }

    // Add a directed edge between two vertices
    public void addEdge(String sourceHashCode, String destinationHashCode, AbstractAction action) {
        if (!adjacencyList.containsKey(sourceHashCode))
            throw new IllegalArgumentException("Source vertices must exist in DAG");

        if (doesPathExist(destinationHashCode, sourceHashCode))
            throw new IllegalArgumentException("Adding this edge would create a cycle!");

        if (StringUtils.isBlank(destinationHashCode)) {
            adjacencyList.get(sourceHashCode).put(action, new Edge(null, new ActionStats()));
        } else {
            adjacencyList.get(sourceHashCode).put(action, new Edge(destinationHashCode, new ActionStats()));
            addVertex(destinationHashCode);
        }
    }

    // Check if a path exists from source to destination
    public boolean doesPathExist(String sourceHashCode, String destinationHashCode) {
        if (StringUtils.isBlank(destinationHashCode)) {
            return false;
        }

        if (!adjacencyList.containsKey(sourceHashCode) || !adjacencyList.containsKey(destinationHashCode))
            return false;

        Set<String> visited = new HashSet<>();
        Stack<String> stack = new Stack<>();
        stack.push(sourceHashCode);

        while (!stack.isEmpty()) {
            String currentHashCode = stack.pop();
            if (currentHashCode.equalsIgnoreCase(destinationHashCode)) {
                return true;
            }
            if (!visited.contains(currentHashCode)) {
                visited.add(currentHashCode);
                Map<AbstractAction, Edge> edges = adjacencyList.get(currentHashCode);
                for (Edge edge : edges.values()) {
                    if (StringUtils.isNotBlank(edge.destination)) {
                        stack.push(edge.destination);
                    }
                }
            }
        }
        return false;
    }

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
            Pair<DAGNode, Deque<Pair<String, AbstractAction>>> policy = graphPolicy(rootNode);

            // Monte carlo rollout: return value of MC rollout from the newly added node
            double delta = policy.a.rollOut();
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

    protected Pair<DAGNode, Deque<Pair<String, AbstractAction>>> graphPolicy(DAGNode node) {
        Deque<Pair<String, AbstractAction>> trajectories = new ArrayDeque<>();
        DAGNode currentNode = node;
        AbstractGameState nextState = currentNode.state.copy();

        while (nextState.isNotTerminal()) {
            String currentHashCode = getHashCode(currentNode.state, currentNode.player.getPlayerID());
            List<AbstractAction> availableActions = currentNode.player.getForwardModel().computeAvailableActions(nextState);

            List<AbstractAction> unexpandedActions = new ArrayList<>();
            for (AbstractAction action : availableActions) {
                if (!getEdges(currentHashCode).containsKey(action)) {
                    addEdge(currentHashCode, null, action);
                    unexpandedActions.add(action);
                } else {
                    if (StringUtils.isBlank(getEdges(currentHashCode).get(action).destination)) {
                        unexpandedActions.add(action);
                    }
                }
            }

            if (!unexpandedActions.isEmpty()) {
                Random r = new Random();
                AbstractAction action = unexpandedActions.get(r.nextInt(unexpandedActions.size()));
                trajectories.push(new Pair<>(currentHashCode, action));
                currentNode.advance(nextState, action.copy());
                String nextHashCode = getHashCode(nextState, currentNode.player.getPlayerID());
                if (isNodeExist(nextHashCode) && doesPathExist(currentHashCode, nextHashCode)) {
                    return new Pair<>(getNode(nextHashCode), trajectories);
                } else {
                    DAGNode newNode = createNewNode(nextState.copy(), nextHashCode);
                    addEdge(currentHashCode, nextHashCode, action);
                    return new Pair<>(newNode, trajectories);
                }
            } else {
                AbstractAction action = ucb(currentHashCode, availableActions);
                trajectories.push(new Pair<>(currentHashCode, action));
                currentNode.advance(nextState, action.copy());
                String nextHashCode = getHashCode(nextState, currentNode.player.getPlayerID());
                if (isNodeExist(nextHashCode) && doesPathExist(currentHashCode, nextHashCode)) {
                    currentNode = getNode(nextHashCode);
                } else {
                    addEdge(currentHashCode, nextHashCode, action);
                    currentNode = createNewNode(nextState.copy(), nextHashCode);
                }
            }
        }

        return new Pair<>(currentNode, trajectories);
    }

    private AbstractAction ucb(String hashCode, List<AbstractAction> availableActions) {
        // Find child with highest UCB value, maximising for ourselves and minimizing for opponent
        Map<AbstractAction, Edge> edges = getEdges(hashCode);
        DAGNode node = getNode(hashCode);
        AbstractAction bestAction = null;
        double bestValue = -Double.MAX_VALUE;
        int totalVisit = getTotalVisit(hashCode);

        for (AbstractAction action: availableActions) {
            ActionStats actionStats = edges.get(action).stats;
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
            boolean iAmMoving = node.state.getCurrentPlayer() == player.getPlayerID();
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

    private int getTotalVisit(String hashCode) {
        int count = 0;
        Map<AbstractAction, Edge> edges = getEdges(hashCode);
        for (AbstractAction action: edges.keySet()) {
            Edge edge = edges.get(action);
            count += edge.stats.getnVisits();
        }
        return count;
    }

    public DAGNode getNode(String hashCode) {
        return transpositionMap.get(hashCode);
    }

    public boolean isNodeExist(String hashCode) {
        return Objects.nonNull(transpositionMap.get(hashCode));
    }

    private DAGNode createNewNode(AbstractGameState nextState, String hashCode) {
        DAGNode newNode = new DAGNode(player, nextState, rnd);
        putNode(hashCode, newNode);
        return newNode;
    }

    public void putNode(String hashCode, DAGNode node) {
        if (!isNodeExist(hashCode)) {
            transpositionMap.put(hashCode, node);
        }
    }

    protected String getHashCode(AbstractGameState state, int playerID) {
        double[] featureVector = player.params.EIStateFeatureVector.featureVector(state, playerID);
        return Arrays.toString(featureVector);
    }

    public AbstractAction bestAction() {
        double bestValue = -Double.MAX_VALUE;
        AbstractAction bestAction = null;
        Map<AbstractAction, Edge> edges = getEdges(rootNode);

        for (Map.Entry<AbstractAction, Edge> action : edges.entrySet()) {
            double childValue = action.getValue().stats.nVisits;

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

    /**
     * Back up the value of the all nodes in trajectories. Increase number of visits and total value.
     *
     * @param result       - value of rollout to back up
     * @param trajectories - sequence of node + action
     */
    protected void backUp(double result, Deque<Pair<String, AbstractAction>> trajectories) {
        while (trajectories.peekLast() != null) {
            Pair<String, AbstractAction> trajectory = trajectories.removeLast();
            backupForAction(result, trajectory);
        }
    }

    private void backupForAction(double result, Pair<String, AbstractAction> trajectory) {
        Map<AbstractAction, Edge> edges = getEdges(trajectory.a);
        Edge edge = edges.get(trajectory.b);
        if (Objects.nonNull(edge)) {
            ActionStats actionStats = edge.stats;
            if (Objects.nonNull(actionStats)) {
                actionStats.nVisits++;
                actionStats.totValue += result;
            } else {
                actionStats = new ActionStats(result, 1);
                edge.stats = actionStats;
            }
        }
    }

    @Override
    public String toString() {
        return adjacencyList.toString();
    }

}
