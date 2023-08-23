package games.loveletter;

import core.AbstractGameState;
import core.components.PartialObservableDeck;
import core.interfaces.IStateFeatureVector;
import games.loveletter.cards.LoveLetterCard;

import java.util.stream.IntStream;

/**
 * A set of features designed to tie in exactly with those used in LoveLetterHeuristic
 */
public class LLStateFeaturesV5 implements IStateFeatureVector {

    String[] names = new String[]{"CARD1", "CARD2",
            "HIDDEN"
    };

    @Override
    public double[] featureVector(AbstractGameState gs, int playerId) {
        LoveLetterGameState llgs = (LoveLetterGameState) gs;

        double[] cardsInHand = new double[3];

        int i = 0;
        for (LoveLetterCard card : llgs.getPlayerHandCards().get(playerId).getComponents()) {
            cardsInHand[i] = card.cardType.getValue();
            i++;
        }

        int visibleCards = 0;
        for (int player = 0; player < llgs.getNPlayers(); player++) {
            if (player != playerId) {
                PartialObservableDeck<LoveLetterCard> deck = llgs.getPlayerHandCards().get(player);
                visibleCards += (int) IntStream.range(0, deck.getSize()).filter(j -> deck.getVisibilityForPlayer(j, playerId)).count();
            }
        }
        cardsInHand[2] = visibleCards / (llgs.getNPlayers() - 1.0);

        return cardsInHand;
    }

    @Override
    public String[] names() {
        return names;
    }

}
