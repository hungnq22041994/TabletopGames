package players.mcgs;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;
import core.interfaces.IStateHeuristic;

import java.util.List;
import java.util.Random;

/**
 * This is a simple version of MCTS that may be useful for newcomers to TAG and MCTS-like algorithms
 * It strips out some of the additional configuration of MCTSPlayer. It uses BasicTreeNode in place of
 * SingleTreeNode.
 */
public class BasicMCGSPlayer extends AbstractPlayer {

    Random rnd;
    MCGSParams params;

    public BasicMCGSPlayer() {
        this(System.currentTimeMillis());
    }

    public BasicMCGSPlayer(long seed) {
        this.params = new MCGSParams(seed);
        rnd = new Random(seed);
        setName("Basic MCGS");

        // These parameters can be changed, and will impact the Basic MCTS algorithm
        this.params.K = Math.sqrt(2);
        this.params.rolloutLength = 10;
        this.params.epsilon = 1e-6;
    }

    public BasicMCGSPlayer(MCGSParams params) {
        this.params = params;
        rnd = new Random(params.getRandomSeed());
        setName("Basic MCGS");
    }

    @Override
    public AbstractAction _getAction(AbstractGameState gameState, List<AbstractAction> allActions) {
        // Search for best action from the root
        BasicGraph graph = new BasicGraph(this, gameState, rnd);

        // mctsSearch does all of the hard work
        graph.mcgsSearch();

//        System.out.println("Total visit of graph:" + graph.getRootNode().getTotalVisit() + " with nodes:" + graph.getTranspositionMap().size());

        // Return best action
        return graph.bestAction();
    }

    public void setStateHeuristic(IStateHeuristic heuristic) {
        this.params.heuristic = heuristic;
    }


    @Override
    public String toString() {
        return "BasicMCGS";
    }

    @Override
    public BasicMCGSPlayer copy() {
        return this;
    }
}