package games.loveletter;

import core.AbstractGameState;
import core.interfaces.IComponentContainer;
import core.interfaces.IStateFeatureVector;
import games.loveletter.cards.LoveLetterCard;
import org.apache.commons.collections.CollectionUtils;

import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.toList;

/**
 * A set of features designed to tie in exactly with those used in LoveLetterHeuristic
 */
public class LLStateFeaturesV3 implements IStateFeatureVector {

    String[] names = new String[]{"CARD1", "CARD2",
            "GUARD_DISCARD", "PRIEST_DISCARD", "BARON_DISCARD", "HANDMAID_DISCARD", "PRINCE_DISCARD", "KING_DISCARD", "COUNTESS_DISCARD", "PRINCESS_DISCARD",
            "LAST_PLAYED_CARD_1", "LAST_PLAYED_CARD_2", "LAST_PLAYED_CARD_3"
    };

    @Override
    public double[] featureVector(AbstractGameState gs, int playerId) {
        LoveLetterGameState llgs = (LoveLetterGameState) gs;
        int discardOffset = 1;
        int lastPlayedCardOffset = 10;

        double[] cardsInHand = new double[13];

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

        for (i = 0; i < llgs.getPlayerDiscardCards().size(); i++) {
            if (i != playerId && CollectionUtils.isNotEmpty(llgs.getPlayerDiscardCards())
                    && Objects.nonNull(llgs.getPlayerDiscardCards().get(i))
                    && CollectionUtils.isNotEmpty(llgs.getPlayerDiscardCards().get(i).getComponents())) {
                cardsInHand[lastPlayedCardOffset + i] = llgs.getPlayerDiscardCards().get(i).get(0).cardType.getValue();
            }
        }

        return cardsInHand;
    }

    @Override
    public String[] names() {
        return names;
    }

}