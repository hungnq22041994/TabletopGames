package players.mcgs;

import core.AbstractPlayer;
import core.Game;
import core.actions.AbstractAction;
import games.GameType;
import games.tictactoe.TicTacToeGameParameters;
import org.junit.Before;
import org.junit.Test;
import players.PlayerConstants;
import players.simple.RandomPlayer;
import utilities.Pair;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MCGSTest {

    DAG dag;

    MCGSPlayer player;

    Game ticTacToe;

    @Before
    public void setup() {
        MCGSParams params = new MCGSParams(1234);
        params.setParameterValue("expertIterationStateFeatures", "games.tictactoe.TicTacToeStateVector");
        player = new MCGSPlayer(params);
        ticTacToe = createTicTacToe(3);
        dag = new DAG(player, ticTacToe.getGameState(), new Random());
    }

    public Game createTicTacToe(int gridSize) {
        List<AbstractPlayer> players = new ArrayList<>();
        players.add(player);
        players.add(new RandomPlayer(new Random(3023)));
        TicTacToeGameParameters gameParams = new TicTacToeGameParameters(3812);
        gameParams.gridSize = gridSize;
        Game game = GameType.TicTacToe.createGameInstance(2, gameParams);
        game.reset(players);
        return game;
    }

    @Test
    public void testMCGSSearch1Iteration() {
        MCGSParams params = new MCGSParams();
        params.setParameterValue("budgetType", PlayerConstants.BUDGET_ITERATIONS);
        params.setParameterValue("budget", 1);
        params.setParameterValue("expertIterationStateFeatures", "games.tictactoe.TicTacToeStateVector");
        player = new MCGSPlayer(params);
        Game ticTacToe = createTicTacToe(3);
        dag = new DAG(player, ticTacToe.getGameState(), new Random());

        dag.mcgsSearch();

        assertEquals(dag.getTranspositionMap().size(), 2);

        for (int i = 0; i < 8; i++) {
            dag.mcgsSearch();
        }

        assertEquals(dag.getTranspositionMap().size(), 10);
    }

    @Test
    public void testGraphPolicy() {
        Pair<DAGNode, Deque<Pair<String, AbstractAction>>> result = dag.graphPolicy(dag.getRootNode());

        assertEquals(dag.getTranspositionMap().size(), 2);
        assertEquals(dag.getEdges(result.a).size(), 0);
        assertEquals(result.b.size(), 1);
    }

    @Test
    public void testBackUp() {
        Deque<Pair<String, AbstractAction>> trajectories = new ArrayDeque<>();
        List<AbstractAction> actions = player.getForwardModel().computeAvailableActions(ticTacToe.getGameState());
        List<DAGNode> nodes = new ArrayList<>();
        actions.forEach(action -> {
            DAGNode node = new DAGNode(player, ticTacToe.getGameState(), new Random());
            nodes.add(node);
            String currentHashCode = dag.getHashCode(node.state, node.player.getPlayerID());
            dag.addEdge(currentHashCode, null, action);
            Pair<String, AbstractAction> trajectory = new Pair<>(currentHashCode, action);
            trajectories.add(trajectory);
        });

        dag.backUp(1, trajectories);

        for (DAGNode node: nodes) {
            ActionStats actionStats = dag.getEdges(node).get(dag.getEdges(node).keySet().stream().findFirst().get()).stats;
            assertNotNull(actionStats);
            assertEquals(actionStats.nVisits, 1);
            assertEquals(actionStats.totValue, 1, 0.01);
        }
    }
}