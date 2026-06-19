package net.nerol.pvp_bot.mixin;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.network.chat.Component;
import net.nerol.pvp_bot.bot.BotPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntitySelectorParser.class)
public abstract class EntitySelectorParserMixin {

    @Redirect(
            method = "parseSelector",
            at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/StringReader;read()C")
    )
    private char pvpbot_handleBotSelector(StringReader reader) {
        char c = reader.read();
        if (c == 'b') {
            ((EntitySelectorParserAccessor) (Object) this)
                    .invokeAddPredicate(entity -> entity instanceof BotPlayer);
            return 'a';
        }
        return c;
    }

    /**
     * Make {@code @b} tab-complete alongside the vanilla {@code @p/@a/@e/...}. Injected at HEAD
     * so the entry accumulates into the same builder this method later builds; brigadier sorts
     * suggestions for display, so insertion order doesn't matter. Gated on {@code allowSelectors}
     * to match when the vanilla letters are offered.
     */
    @Inject(method = "fillSelectorSuggestions", at = @At("HEAD"))
    private static void pvpbot_suggestBotSelector(SuggestionsBuilder builder, CallbackInfo ci) {
        builder.suggest("@b", Component.literal("All bots"));
    }
}