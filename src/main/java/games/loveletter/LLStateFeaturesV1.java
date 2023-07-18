package games.loveletter;

import core.AbstractGameState;
import core.components.PartialObservableDeck;
import core.interfaces.IStateFeatureVector;
import games.loveletter.cards.LoveLetterCard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static games.loveletter.cards.LoveLetterCard.CardType.*;

/**
 * A set of features designed to tie in exactly with those used in LoveLetterHeuristic
 */
public class LLStateFeaturesV1 implements IStateFeatureVector {

    String[] names = new String[]{"CARD1", "CARD2"};

    @Override
    public double[] featureVector(AbstractGameState gs, int playerId) {
        LoveLetterGameState llgs = (LoveLetterGameState) gs;

        List<Double> cardsInHand = new ArrayList<>();

        for (LoveLetterCard card : llgs.getPlayerHandCards().get(playerId).getComponents()) {
            cardsInHand.add((double) card.cardType.getValue());
        }

        return cardsInHand.stream().mapToDouble(d -> d).toArray();
    }

    @Override
    public String[] names() {
        return names;
    }

}
