package net.nerol.pvp_bot.bot.action;

import java.util.Random;

/**
 * Test class for random actions
 */
public class RandomAction {
    public int randomYaw() {
        return (new Random()).nextInt(180 - (-180) + 1) + (-180);
    }

    public int randomPitch() {
        return (new Random()).nextInt(90 - (-90) + 1) + (-90);
    }

    public ActionType randomAction() {
        ActionType type = ActionType.getRandomAction();

        return type;
    }
}
