package players.mcgs;

import core.AbstractGameState;
import core.interfaces.IStateFeatureVector;
import core.interfaces.IStateHeuristic;
import evaluation.TunableParameters;
import players.PlayerParameters;

import java.util.Arrays;

public class MCGSParams extends PlayerParameters {

    public double K = Math.sqrt(2);
    public int rolloutLength = 10;
    public double epsilon = 1e-6;

    public String expertIterationStateFeatures = "";
    public IStateFeatureVector EIStateFeatureVector;

    public IStateHeuristic heuristic = AbstractGameState::getHeuristicScore;

    public MCGSParams() {
        this(System.currentTimeMillis());
    }

    public MCGSParams(long seed) {
        super(seed);
        addTunableParameter("K", Math.sqrt(2), Arrays.asList(0.0, 0.1, 1.0, Math.sqrt(2), 3.0, 10.0));
        addTunableParameter("rolloutLength", 10, Arrays.asList(0, 3, 10, 30, 100));
        addTunableParameter("epsilon", 1e-6);
        addTunableParameter("expertIterationStateFeatures",  "");
        addTunableParameter("heuristic", (IStateHeuristic) AbstractGameState::getHeuristicScore);
    }

    @Override
    public void _reset() {
        super._reset();
        K = (double) getParameterValue("K");
        rolloutLength = (int) getParameterValue("rolloutLength");
        epsilon = (double) getParameterValue("epsilon");
        expertIterationStateFeatures = (String) getParameterValue("expertIterationStateFeatures");
        if (!expertIterationStateFeatures.equals("")) {
            try {
                EIStateFeatureVector = (IStateFeatureVector) Class.forName(expertIterationStateFeatures).getConstructor().newInstance();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        heuristic = (IStateHeuristic) getParameterValue("heuristic");
        if (heuristic instanceof TunableParameters) {
            TunableParameters tunableHeuristic = (TunableParameters) heuristic;
            for (String name : tunableHeuristic.getParameterNames()) {
                tunableHeuristic.setParameterValue(name, this.getParameterValue("heuristic." + name));
            }
        }
    }


    @Override
    protected MCGSParams _copy() {
        MCGSParams retValue = new MCGSParams(System.currentTimeMillis());
        retValue.K = K;
        retValue.rolloutLength = rolloutLength;
        retValue.epsilon = epsilon;
        retValue.expertIterationStateFeatures = expertIterationStateFeatures;
        retValue.EIStateFeatureVector = EIStateFeatureVector;
        retValue.heuristic = heuristic;
        return retValue;
    }

    public IStateHeuristic getHeuristic() {
        return heuristic;
    }

    @Override
    public MCGSPlayer instantiate() {
        return new MCGSPlayer(this);
    }

}
