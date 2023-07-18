package games.loveletter;

import core.AbstractGameState;
import core.interfaces.IComponentContainer;
import core.interfaces.IStateFeatureVector;
import games.loveletter.cards.LoveLetterCard;

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * A set of features designed to tie in exactly with those used in LoveLetterHeuristic
 */
public class LLStateFeaturesV2 implements IStateFeatureVector {

    String[] names = new String[]{"CARD1", "CARD2",
            "GUARD_DISCARD", "PRIEST_DISCARD", "BARON_DISCARD", "HANDMAID_DISCARD", "PRINCE_DISCARD", "KING_DISCARD", "COUNTESS_DISCARD", "PRINCESS_DISCARD"};

    @Override
    public double[] featureVector(AbstractGameState gs, int playerId) {
        LoveLetterGameState llgs = (LoveLetterGameState) gs;
        int discardOffset = 1;

        double[] cardsInHand = new double[10];

        int i = 0;
        for (LoveLetterCard card : llgs.getPlayerHandCards().get(playerId).getComponents()) {
            cardsInHand[i] = card.cardType.getValue();
            i++;
        }

        List<LoveLetterCard> discardDecks = llgs.getPlayerDiscardCards().stream()
                .flatMap(IComponentContainer::stream)
                .collect(toList());

        for (LoveLetterCard discard : discardDecks) {
            cardsInHand[discardOffset + discard.cardType.getValue()] += 1.0;
        }

        return cardsInHand;
    }

    @Override
    public String[] names() {
        return names;
    }

}
