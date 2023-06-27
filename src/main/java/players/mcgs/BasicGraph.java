package players.mcgs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class BasicGraph {
    private Map<BasicGraphNode, List<BasicGraphNode>> adjVertices;

    //    State S -> action a -> hash the State(s,a) -> put in transpositionMap, hash value ,
    //    if we advance from a random state, with action -> look in to the transpositionMap to find the state
    //    tic tac toe -> several hash version, get the game state (position of x and o)
    //    hash function H(s)
    private final Map<Integer, BasicGraphNode> transpositionMap = new HashMap<>();

    // Number of FM calls and State copies for this graph
    private int fmCallsCount;

    public void putNode(int hashCode, BasicGraphNode node) {
        if (!isNodeExist(hashCode)) {
            transpositionMap.put(hashCode, node);
        }
    }

    public BasicGraphNode putIfAbsent(int hashCode, BasicGraphNode node) {
        if (!isNodeExist(hashCode)) {
             return transpositionMap.put(hashCode, node);
        } else {
            return transpositionMap.get(hashCode);
        }
    }

    public boolean isNodeExist(int hashCode) {
        return Objects.nonNull(transpositionMap.get(hashCode));
    }

    public BasicGraphNode getNode(int hashCode) {
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
}
