package net.nerol.pvp_bot.mixin;

import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.Predicate;

/** Exposes {@code EntitySelectorParser.addPredicate} so the {@code @b} selector can attach its
 *  "is a BotPlayer" filter. */
@Mixin(EntitySelectorParser.class)
public interface EntitySelectorParserAccessor {
    @Invoker("addPredicate")
    void invokeAddPredicate(Predicate<Entity> predicate);
}
