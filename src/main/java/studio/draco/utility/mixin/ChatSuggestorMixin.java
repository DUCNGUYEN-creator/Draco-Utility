/*
 * Draco Utility - client framework for Minecraft (Fabric)
 * Copyright (C) 2026 DUCNGUYEN-creator (Draco Studio)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package studio.draco.utility.mixin;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.draco.utility.Draco;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Tab / popup completion for Draco chat commands (for example ".toggle es"). Vanilla commands are untouched. */
@Mixin(ChatInputSuggestor.class)
public abstract class ChatSuggestorMixin {
    @Shadow @Final TextFieldWidget textField;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow public abstract void show(boolean narrateFirstSuggestion);

    @Inject(method = "refresh", at = @At("TAIL"), require = 0)
    private void dracoutility$completeDracoCommands(CallbackInfo ci) {
        Draco d = Draco.maybe();
        if (d == null) return;
        String text = textField.getText();
        String before = text.substring(0, Math.min(textField.getCursor(), text.length()));
        if (!d.commands.isCommand(before)) return;

        List<String> options = d.commands.suggest(before);
        int start = Math.max(before.lastIndexOf(' ') + 1, d.commands.prefix().length());
        SuggestionsBuilder builder = new SuggestionsBuilder(before, start);
        for (String option : options) builder.suggest(option);
        pendingSuggestions = CompletableFuture.completedFuture(builder.build());
        show(false);
    }
}
