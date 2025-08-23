package hu.snowylol.networkjs;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(NetworkJS.MODID)
public class NetworkJSNeoForge {
    public NetworkJSNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NetworkJS.init();
        
        // Register command
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        
        // Handle server lifecycle
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }
    
    private void onRegisterCommands(RegisterCommandsEvent event) {
        NetworkJSCommand.register(event.getDispatcher());
    }
    
    private void onServerStarting(ServerStartingEvent event) {
        NetworkJS.LOGGER.info("Server starting - NetworkJS ready");
        // Check for singleplayer and show warning after server is started
        NetworkJS.checkSingleplayerAndWarn();
    }
    
    private void onServerStopping(ServerStoppingEvent event) {
        NetworkJS.disableRegistry();
        NetworkJS.LOGGER.info("Server stopping - NetworkJS registry disabled");
    }
}
