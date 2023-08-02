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
            if (entry.getValue().getTotalVisit() == 1) {
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


    @Override
    public String toString() {
        StringBuilder retValue = new StringBuilder();
        retValue.append(String.format("%d nodes, %d leaves, %d terminal nodes, %d max actions, with maximum depth %d\n", totalNodes, totalLeaves, totalTerminalNodes, maxActionsAtNode, depthReached));
        return retValue.toString();
    }
}
