package hu.snowylol.networkjs;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class NetworkJSCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("networkjs")
            .requires(source -> source.hasPermission(2)) // Requires op level 2
            .then(Commands.literal("enable")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    if (NetworkJS.isRegistryEnabled()) {
                        source.sendFailure(Component.literal("NetworkJS registry is already enabled!"));
                        return 0;
                    }
                    
                    NetworkJS.enableRegistry();
                    source.sendSuccess(() -> Component.literal("NetworkJS registry enabled successfully!"), true);
                    return 1;
                }))
            .then(Commands.literal("disable")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    if (!NetworkJS.isRegistryEnabled()) {
                        source.sendFailure(Component.literal("NetworkJS registry is already disabled!"));
                        return 0;
                    }
                    
                    NetworkJS.disableRegistry();
                    source.sendSuccess(() -> Component.literal("NetworkJS registry disabled!"), true);
                    return 1;
                }))
            .then(Commands.literal("reload")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    if (!NetworkJS.isRegistryEnabled()) {
                        source.sendFailure(Component.literal("NetworkJS registry is disabled! Enable it first with /networkjs enable"));
                        return 0;
                    }
                    
                    NetworkJS.forceReloadBindings();
                    source.sendSuccess(() -> Component.literal("NetworkJS bindings reload requested!"), true);
                    return 1;
                }))
            .then(Commands.literal("status")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    boolean enabled = NetworkJS.isRegistryEnabled();
                    String status = enabled ? "enabled" : "disabled";
                    String color = enabled ? "&a" : "&c";
                    
                    source.sendSuccess(() -> Component.literal(color + "[NetworkJS] Registry is currently " + status), false);
                    return 1;
                }))
        );
    }
}
