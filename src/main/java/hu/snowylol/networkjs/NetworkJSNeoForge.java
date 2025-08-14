package hu.snowylol.networkjs;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

@Mod(NetworkJS.MODID)
public class NetworkJSNeoForge {
    public NetworkJSNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NetworkJS.init();
    }
}
