package forge.ai.ability;

import forge.ai.ComputerUtil;
import forge.ai.ComputerUtilCard;
import forge.ai.ComputerUtilCost;
import forge.ai.SpellAbilityAi;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates.Presets;
import forge.game.cost.Cost;
import forge.game.cost.CostTap;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.PlayerCollection;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.game.spellability.TargetRestrictions;
import forge.game.zone.ZoneType;

import java.util.List;

public class UntapAi extends SpellAbilityAi {
    @Override
    protected boolean checkAiLogic(final Player ai, final SpellAbility sa, final String aiLogic) {
        final Card source = sa.getHostCard();
        if ("EOT".equals(sa.getParam("AILogic")) && (source.getGame().getPhaseHandler().getNextTurn() != ai
                || !source.getGame().getPhaseHandler().getPhase().equals(PhaseType.END_OF_TURN))) {
            return false;
        }

        return !("Never".equals(aiLogic));
    }

    @Override
    protected boolean willPayCosts(final Player ai, final SpellAbility sa, final Cost cost, final Card source) {
        if (!ComputerUtilCost.checkAddM1M1CounterCost(cost, source)) {
            return false;
        }

        if (!ComputerUtilCost.checkDiscardCost(ai, cost, sa.getHostCard())) {
            return false;
        }

        return true;
    }

    @Override
    protected boolean checkApiLogic(Player ai, SpellAbility sa) {
        final TargetRestrictions tgt = sa.getTargetRestrictions();
        final Card source = sa.getHostCard();

        if (ComputerUtil.preventRunAwayActivations(sa)) {
            return false;
        }

        if (tgt == null) {
            final List<Card> pDefined = AbilityUtils.getDefinedCards(source, sa.getParam("Defined"), sa);
            if (pDefined != null && pDefined.get(0).isUntapped() && pDefined.get(0).getController() == ai) {
                return false;
            }
        } else {
            if (!untapPrefTargeting(ai, tgt, sa, false)) {
                return false;
            }
        }

        return true;
    }

    @Override
    protected boolean doTriggerAINoCost(Player ai, SpellAbility sa, boolean mandatory) {
        final TargetRestrictions tgt = sa.getTargetRestrictions();

        if (tgt == null) {
            if (mandatory) {
                return true;
            }

            // TODO: use Defined to determine, if this is an unfavorable result
            final List<Card> pDefined = AbilityUtils.getDefinedCards(sa.getHostCard(), sa.getParam("Defined"), sa);
            if (pDefined != null && pDefined.get(0).isUntapped() && pDefined.get(0).getController() == ai) {
                return false;
            }

            return true;
        } else {
            if (untapPrefTargeting(ai, tgt, sa, mandatory)) {
                return true;
            } else if (mandatory) {
                // not enough preferred targets, but mandatory so keep going:
                return untapUnpreferredTargeting(sa, mandatory);
            }
        }

        return false;
    }

    @Override
    public boolean chkAIDrawback(SpellAbility sa, Player ai) {
        final TargetRestrictions tgt = sa.getTargetRestrictions();

        boolean randomReturn = true;

        if (tgt == null) {
            // who cares if its already untapped, it's only a subability?
        } else {
            if (!untapPrefTargeting(ai, tgt, sa, false)) {
                return false;
            }
        }

        return randomReturn;
    }

    /**
     * <p>
     * untapPrefTargeting.
     * </p>
     * 
     * @param tgt
     *            a {@link forge.game.spellability.TargetRestrictions} object.
     * @param sa
     *            a {@link forge.game.spellability.SpellAbility} object.
     * @param mandatory
     *            a boolean.
     * @return a boolean.
     */
    private static boolean untapPrefTargeting(final Player ai, final TargetRestrictions tgt, final SpellAbility sa, final boolean mandatory) {
        final Card source = sa.getHostCard();

        Player targetController = ai;

        if (sa.isCurse()) {
            targetController = ComputerUtil.getOpponentFor(ai);
        }

        CardCollection list = CardLists.getTargetableCards(targetController.getCardsIn(ZoneType.Battlefield), sa);
        list = CardLists.getValidCards(list, tgt.getValidTgts(), source.getController(), source, sa);

        if (list.isEmpty()) {
            return false;
        }

        CardCollection untapList = CardLists.filter(list, Presets.TAPPED);
        // filter out enchantments and planeswalkers, their tapped state doesn't
        // matter.
        final String[] tappablePermanents = {"Creature", "Land", "Artifact"};
        untapList = CardLists.getValidCards(untapList, tappablePermanents, source.getController(), source, sa);

        // Try to avoid potential infinite recursion,
        // e.g. Kiora's Follower untapping another Kiora's Follower and repeating infinitely
        if (sa.getPayCosts() != null && sa.getPayCosts().hasOnlySpecificCostType(CostTap.class)) {
            CardCollection toRemove = new CardCollection();
            for (Card c : untapList) {
                for (SpellAbility ab : c.getAllSpellAbilities()) {
                    if (ab.getApi() == ApiType.Untap
                            && ab.getPayCosts() != null
                            && ab.getPayCosts().hasOnlySpecificCostType(CostTap.class)
                            && ab.canTarget(source)) {
                        toRemove.add(c);
                        break;
                    }
                }
            }
            untapList.removeAll(toRemove);
        }

        //try to exclude things that will already be untapped due to something on stack or because something is
        //already targeted in a parent or sub SA
        CardCollection toExclude = new CardCollection();
        SpellAbility saSub = sa.getRootAbility();
        while (saSub != null) {
            for (Card c : untapList) {
                if (saSub.getApi() == ApiType.Untap) {
                    if (saSub.getTargets() != null && saSub.getTargets().getTargetCards().contains(c)) {
                        // Was already targeted in a parent or sub SA
                        toExclude.add(c);
                    }
                }
            }
            saSub = saSub.getSubAbility();
        }
        for (SpellAbilityStackInstance si : ai.getGame().getStack()) {
            SpellAbility ab = si.getSpellAbility(false);
            if (ab != null && ab.getApi() == ApiType.Tap) {
                for (Card c : untapList) {
                    // TODO: somehow ensure that the untapping SA won't be countered
                    if (si.getTargetChoices() != null && si.getTargetChoices().getTargetCards().contains(c)) {
                        toExclude.add(c);
                    }
                }
            }
        }
        untapList.removeAll(toExclude);

        sa.resetTargets();
        while (sa.getTargets().getNumTargeted() < tgt.getMaxTargets(sa.getHostCard(), sa)) {
            Card choice = null;

            if (untapList.isEmpty()) {
                // Animate untapped lands (Koth of the Hamer)
                if (sa.getSubAbility() != null && sa.getSubAbility().getApi() == ApiType.Animate && !list.isEmpty()
                        && ai.getGame().getPhaseHandler().getPhase().isBefore(PhaseType.COMBAT_DECLARE_ATTACKERS)) {
                    choice = ComputerUtilCard.getWorstPermanentAI(list, false, false, false, false);
                } else if (sa.getTargets().getNumTargeted() < tgt.getMinTargets(sa.getHostCard(), sa) || sa.getTargets().getNumTargeted() == 0) {
                    sa.resetTargets();
                    return false;
                } else {
                    // TODO is this good enough? for up to amounts?
                    break;
                }
            } else {
                //Untap Time Vault? - Yes please!
                for (Card c : untapList) {
                    if (c.getName().equals("Time Vault")) {
                        choice = c;
                        break;
                    }
                }
                if (choice == null) {
                    if (CardLists.getNotType(untapList, "Creature").isEmpty()) {
                        choice = ComputerUtilCard.getBestCreatureAI(untapList); // if only creatures take the best
                    } else if (!sa.getPayCosts().hasManaCost() || sa.getRootAbility().isTrigger()) {
                        choice = ComputerUtilCard.getMostExpensivePermanentAI(untapList, sa, false);
                    }
                }
            }

            if (choice == null) { // can't find anything left
                if (sa.getTargets().getNumTargeted() < tgt.getMinTargets(sa.getHostCard(), sa) || sa.getTargets().getNumTargeted() == 0) {
                    sa.resetTargets();
                    return false;
                } else {
                    // TODO is this good enough? for up to amounts?
                    break;
                }
            }

            untapList.remove(choice);
            list.remove(choice);
            sa.getTargets().add(choice);
        }
        return true;
    }

    /**
     * <p>
     * untapUnpreferredTargeting.
     * </p>
     *
     * @param sa
     *            a {@link forge.game.spellability.SpellAbility} object.
     * @param mandatory
     *            a boolean.
     * @return a boolean.
     */
    private boolean untapUnpreferredTargeting(final SpellAbility sa, final boolean mandatory) {
        final Card source = sa.getHostCard();
        final TargetRestrictions tgt = sa.getTargetRestrictions();

        CardCollection list = CardLists.getValidCards(source.getGame().getCardsIn(ZoneType.Battlefield),
                tgt.getValidTgts(), source.getController(), source, sa);
        list = CardLists.getTargetableCards(list, sa);

        // filter by enchantments and planeswalkers, their tapped state doesn't
        // matter.
        final String[] tappablePermanents = { "Enchantment", "Planeswalker" };
        CardCollection tapList = CardLists.getValidCards(list, tappablePermanents, source.getController(), source, sa);

        if (untapTargetList(source, tgt, sa, mandatory, tapList)) {
            return true;
        }

        // try to just tap already tapped things
        tapList = CardLists.filter(list, Presets.UNTAPPED);

        if (untapTargetList(source, tgt, sa, mandatory, tapList)) {
            return true;
        }

        // just tap whatever we can
        tapList = list;

        if (untapTargetList(source, tgt, sa, mandatory, tapList)) {
            return true;
        }

        return false;
    }

    private boolean untapTargetList(final Card source, final TargetRestrictions tgt, final SpellAbility sa, final boolean mandatory, 
    		final CardCollection tapList) {
        for (final Card c : sa.getTargets().getTargetCards()) {
            tapList.remove(c);
        }

        if (tapList.isEmpty()) {
            return false;
        }

        while (sa.getTargets().getNumTargeted() < tgt.getMaxTargets(source, sa)) {
            Card choice = null;

            if (tapList.isEmpty()) {
                if (sa.getTargets().getNumTargeted() < tgt.getMinTargets(source, sa) || sa.getTargets().getNumTargeted() == 0) {
                    if (!mandatory) {
                        sa.resetTargets();
                    }
                    return false;
                } else {
                    // TODO is this good enough? for up to amounts?
                    break;
                }
            }

            if (CardLists.getNotType(tapList, "Creature").isEmpty()) {
                choice = ComputerUtilCard.getBestCreatureAI(tapList); // if only creatures take the best
            } else {
                choice = ComputerUtilCard.getMostExpensivePermanentAI(tapList, sa, false);
            }

            if (choice == null) { // can't find anything left
                if (sa.getTargets().getNumTargeted() < tgt.getMinTargets(sa.getHostCard(), sa) || sa.getTargets().getNumTargeted() == 0) {
                    if (!mandatory) {
                        sa.resetTargets();
                    }
                    return false;
                } else {
                    // TODO is this good enough? for up to amounts?
                    break;
                }
            }

            tapList.remove(choice);
            sa.getTargets().add(choice);
        }

        return true;
    }
    
    @Override
    public Card chooseSingleCard(Player ai, SpellAbility sa, Iterable<Card> list, boolean isOptional, Player targetedPlayer) {
        PlayerCollection pl = new PlayerCollection();
        pl.add(ai);
        pl.addAll(ai.getAllies());
        return ComputerUtilCard.getBestAI(CardLists.filterControlledBy(list, pl));
    }
}
