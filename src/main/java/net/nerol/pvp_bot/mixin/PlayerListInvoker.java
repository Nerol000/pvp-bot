package net.nerol.pvp_bot.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerList.class)
public interface PlayerListInvoker {
    @Invoker("save")
    void invokeSave(ServerPlayer player);
}
