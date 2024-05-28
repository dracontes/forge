package forge.gamemodes.limited;

import forge.card.ColorSet;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgePreferences;

import java.util.Collections;
import java.util.List;

public class LimitedPlayerAI extends LimitedPlayer {
    protected DeckColors deckCols;

    public LimitedPlayerAI(int seatingOrder, BoosterDraft draft) {
        super(seatingOrder, draft);
        deckCols = new DeckColors();
    }

    @Override
    public PaperCard chooseCard() {
        if (packQueue.isEmpty()) {
            return null;
        }

        List<PaperCard> chooseFrom = packQueue.peek();
        if (chooseFrom.isEmpty()) {
            return null;
        }

        CardPool pool = deck.getOrCreate(DeckSection.Sideboard);
        if (ForgePreferences.DEV_MODE) {
            System.out.println("Player[" + order + "] pack: " + chooseFrom.toString());
        }

        // TODO Archdemon of Paliano random draft while active


        final ColorSet chosenColors = deckCols.getChosenColors();
        final boolean canAddMoreColors = deckCols.canChoseMoreColors();

        List<PaperCard> rankedCards = CardRanker.rankCardsInPack(chooseFrom, pool.toFlatList(), chosenColors, canAddMoreColors);
        PaperCard bestPick = rankedCards.get(0);

        if (canAddMoreColors) {
            deckCols.addColorsOf(bestPick);
        }

        if (ForgePreferences.DEV_MODE) {
            System.out.println("Player[" + order + "] picked: " + bestPick);
        }

        return bestPick;
    }

    public Deck buildDeck(String landSetCode) {
        CardPool section = deck.getOrCreate(DeckSection.Sideboard);
        return new BoosterDeckBuilder(section.toFlatList(), deckCols).buildDeck(landSetCode);
    }

    @Override
    protected String chooseColor(List<String> colors, LimitedPlayer player, String title) {
        if (player.equals(this)) {
            // For Paliano, choose one of my colors
            // For Regicide, random is fine?
        } else {
            // For Paliano, if player has revealed anything, try to avoid that color
            // For Regicide, don't choose one of my colors
        }
        Collections.shuffle(colors);
        return colors.get(0);
    }

    @Override
    protected boolean removeWithAnimus(PaperCard bestPick) {
        // TODO Animus of Predation logic
        // Feel free to remove any cards that we won't play and can give us a bonus
        // We should verify we don't already have the keyword bonus that card would grant
        return false;
    }

    @Override
    protected boolean revealWithBanneret(PaperCard bestPick) {
        // Just choose the first creature that we haven't noted yet.
        // This is a very simple heuristic, but it's good enough for now.
        if (!bestPick.getRules().getType().isCreature()) {
            return false;
        }

        List<String> nobleBanneret = getDraftNotes().getOrDefault("Noble Banneret", null);
        return nobleBanneret == null || !nobleBanneret.contains(bestPick.getName());
    }
}
