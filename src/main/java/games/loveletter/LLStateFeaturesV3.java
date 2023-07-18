package games.loveletter;

import core.AbstractGameState;
import core.components.PartialObservableDeck;
import core.interfaces.IComponentContainer;
import core.interfaces.IStateFeatureVector;
import games.loveletter.cards.LoveLetterCard;

import java.util.List;
import java.util.stream.IntStream;

import static games.loveletter.cards.LoveLetterCard.CardType.getMaxCardValue;
import static java.util.stream.Collectors.toList;

/**
 * A set of features designed to tie in exactly with those used in LoveLetterHeuristic
 */
public class LLStateFeaturesV3 implements IStateFeatureVector {

    String[] names = new String[]{
            "PROTECTED", "HIDDEN", "CARDS", "DRAW_DECK",
            "GUARD", "PRIEST", "BARON", "HANDMAID", "PRINCE", "KING", "COUNTESS", "PRINCESS",
            "GUARD_KNOWN", "PRIEST_KNOWN", "BARON_KNOWN", "HANDMAID_KNOWN", "PRINCE_KNOWN", "KING_KNOWN", "COUNTESS_KNOWN", "PRINCESS_KNOWN",
            "GUARD_DISCARD", "PRIEST_DISCARD", "BARON_DISCARD", "HANDMAID_DISCARD", "PRINCE_DISCARD", "KING_DISCARD", "COUNTESS_DISCARD", "PRINCESS_DISCARD",
            "GUARD_OTHER", "PRIEST_OTHER", "BARON_OTHER", "HANDMAID_OTHER", "PRINCE_OTHER", "KING_OTHER", "COUNTESS_OTHER", "PRINCESS_OTHER"
    };

    @Override
    public double[] featureVector(AbstractGameState gs, int playerId) {
        LoveLetterGameState state = (LoveLetterGameState) gs;

        double[] retValue = new double[names.length];
        int cardsOwnedOffset = 3;
        int cardsKnownOffset = 11;
        int discardOffset = 19;
        int otherOffset = 27;

        double cardValues = 0;
        PartialObservableDeck<LoveLetterCard> hand = state.getPlayerHandCards().get(playerId);
        for (int i = 0; i < hand.getSize(); i++) {
            boolean[] visibility = hand.getVisibilityOfComponent(i);
            LoveLetterCard card = hand.get(i);
            cardValues += card.cardType.getValue();
            int value = card.cardType.getValue();
            retValue[cardsOwnedOffset + value] = 1.0;
            for (int j = 0; j < visibility.length; j++) {
                if (j == playerId)
                    continue;
                if (visibility[j]) {
                    retValue[cardsKnownOffset + value] = 1.0;
                    break;
                }
            }
        }

        double maxCardValue = 1 + state.getPlayerHandCards().get(playerId).getSize() * getMaxCardValue();

        int visibleCards = 0;
        for (int player = 0; player < state.getNPlayers(); player++) {
            if (player != playerId) {
                PartialObservableDeck<LoveLetterCard> deck = state.getPlayerHandCards().get(player);
                for (int i = 0; i < deck.getSize(); i++) {
                    if (deck.getVisibilityOfComponent(i)[playerId]) {
                        visibleCards++;
                        retValue[otherOffset + deck.getComponents().get(i).cardType.getValue()] = 1.0;
                    }
                }
                visibleCards += (int) IntStream.range(0, deck.getSize()).filter(i -> deck.getVisibilityForPlayer(i, playerId)).count();
            }
        }
        List<LoveLetterCard> discardDecks = state.getPlayerDiscardCards().stream()
                .flatMap(IComponentContainer::stream)
                .collect(toList());
        for (LoveLetterCard discard : discardDecks) {
            retValue[discardOffset + discard.cardType.getValue()] += 1.0;
        }
        // divide by total cards
        retValue[1 + discardOffset] /= 5.0;
        for (int i = 2; i <= 5; i++)
            retValue[i + discardOffset] /= 2.0;

        retValue[0] = state.isProtected(playerId) ? 1.0 : 0.0;
        retValue[1] = visibleCards / (state.getNPlayers() - 1.0);
        retValue[2] = cardValues / maxCardValue;
        retValue[3] = state.getDrawPile().getSize() / 16.0;

        return retValue;
    }

    @Override
    public String[] names() {
        return names;
    }

}
