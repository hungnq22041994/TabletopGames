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

public class BasicGraphTest {

    BasicGraph basicGraph;

    BasicMCGSPlayer player;

    Game ticTacToe;

    @Before
    public void setup() {
        MCGSParams params = new MCGSParams(1234);
        params.setParameterValue("expertIterationStateFeatures", "games.tictactoe.TicTacToeStateVector");
        player = new BasicMCGSPlayer(params);
        ticTacToe = createTicTacToe(3);
        basicGraph = new BasicGraph(player, ticTacToe.getGameState(), new Random());
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
        player = new BasicMCGSPlayer(params);
        Game ticTacToe = createTicTacToe(3);
        basicGraph = new BasicGraph(player, ticTacToe.getGameState(), new Random());

        basicGraph.mcgsSearch();

        assertEquals(basicGraph.getTranspositionMap().size(), 2);

        for (int i = 0; i < 8; i++) {
            basicGraph.mcgsSearch();
        }

        assertEquals(basicGraph.getTranspositionMap().size(), 10);
    }

    @Test
    public void testGraphPolicy() {
        Deque<Pair<BasicGraphNode, AbstractAction>> trajectories = new ArrayDeque<>();
        BasicGraphNode node = basicGraph.graphPolicy(basicGraph.getRootNode(), trajectories);

        assertEquals(basicGraph.getTranspositionMap().size(), 2);
        assertEquals(node.actionStatsMap.size(), 0);
        assertEquals(trajectories.size(), 1);
    }

    @Test
    public void testBackUp() {
        Deque<Pair<BasicGraphNode, AbstractAction>> trajectories = new ArrayDeque<>();
        List<AbstractAction> actions = player.getForwardModel().computeAvailableActions(ticTacToe.getGameState());
        List<BasicGraphNode> nodes = new ArrayList<>();
        actions.forEach(action -> {
            BasicGraphNode node = new BasicGraphNode(player, ticTacToe.getGameState(), new Random());
            nodes.add(node);
            Pair<BasicGraphNode, AbstractAction> trajectory = new Pair<>(node, action);
            trajectories.add(trajectory);
        });

        basicGraph.backUp(1, trajectories);

        for (BasicGraphNode node: nodes) {
            ActionStats actionStats = node.actionStatsMap.get(node.actionStatsMap.keySet().stream().findFirst().get());
            assertNotNull(actionStats);
            assertEquals(actionStats.nVisits, 1);
            assertEquals(actionStats.totValue, 1, 0.01);
        }
    }
}