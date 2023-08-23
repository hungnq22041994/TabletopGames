package players.mcgs;

import java.util.Map;

public class GraphStatistics {

    private int depthReached;
    private int totalNodes;
    private int totalLeaves;
    private int totalTerminalNodes;
    private int maxActionsAtNode;

    public GraphStatistics(BasicGraph graph) {
        Map<String, BasicGraphNode> nodeMap = graph.getTranspositionMap();
        depthReached = graph.depthReached;
        totalNodes = nodeMap.size();
        for (Map.Entry<String, BasicGraphNode> entry : nodeMap.entrySet()) {
            if (entry.getValue().getTotalVisit() == 0) {
                totalLeaves++;
            }
            if (entry.getValue().isTerminal) {
                totalTerminalNodes++;
            }
            if (maxActionsAtNode < entry.getValue().actionStatsMap.size()) {
                maxActionsAtNode = entry.getValue().actionStatsMap.size();
            }
        }
    }

    public GraphStatistics(DAG graph) {
        Map<String, DAGNode> nodeMap = graph.getTranspositionMap();
        depthReached = graph.depthReached;
        totalNodes = nodeMap.size();
        for (Map.Entry<String, DAGNode> entry : nodeMap.entrySet()) {
            if (graph.getTotalVisit(entry.getKey()) == 0) {
                totalLeaves++;
            }
            if (entry.getValue().isTerminal) {
                totalTerminalNodes++;
            }
            if (maxActionsAtNode < graph.getEdges(entry.getKey()).size()) {
                maxActionsAtNode = graph.getEdges(entry.getKey()).size();
            }
        }
    }


    @Override
    public String toString() {
        StringBuilder retValue = new StringBuilder();
        retValue.append(String.format("%d nodes, %d leaves, %d terminal nodes, %d max actions, with maximum depth %d\n", totalNodes, totalLeaves, totalTerminalNodes, maxActionsAtNode, depthReached));
        return retValue.toString();
    }
}
