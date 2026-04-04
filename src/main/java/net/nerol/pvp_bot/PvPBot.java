package net.nerol.pvp_bot;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.nerol.pvp_bot.commands.BotCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PvPBot implements ModInitializer {
	public static final String MOD_ID = "pvp_bot";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {

        LOGGER.info("Hello Fabric world!");

        BotCommand.register();
    }
}