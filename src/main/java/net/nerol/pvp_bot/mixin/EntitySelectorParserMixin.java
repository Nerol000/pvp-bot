package net.nerol.pvp_bot.mixin;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.network.chat.Component;
import net.nerol.pvp_bot.bot.BotPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Adds a custom {@code @b} target selector that matches every {@link BotPlayer}.
 *
 * <p>It piggy-backs on the vanilla {@code @a} ("all players") path: when the selector character
 * read by {@code parseSelector} is {@code 'b'}, we attach an "is a BotPlayer" predicate and
 * return {@code 'a'} instead. The rest of {@code @a}'s setup — sourcing from the player list, the
 * unlimited result count, {@code [options]} parsing, and suggestions — then runs unchanged, and
 * the predicate narrows the matches to bots. Bots are {@code ServerPlayer}s, so {@code @a} already
 * sees them; this just filters everything else out.
 */
@Mixin(EntitySelectorParser.class)
public abstract class EntitySelectorParserMixin {

    @Shadow @Final private boolean allowSelectors;

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
    private void pvpbot_suggestBotSelector(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer,
                                           CallbackInfoReturnable<CompletableFuture<Suggestions>> cir) {
        if (this.allowSelectors) {
            builder.suggest("@b", Component.literal("All bots"));
        }
    }
}
