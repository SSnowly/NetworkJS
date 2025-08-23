package hu.snowylol.networkjs;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import hu.snowylol.networkjs.FetchBinding.FetchOptions;

import java.util.Map;

public class NetworkJSPlugin implements KubeJSPlugin {
    
    @Override
    public void registerBindings(BindingRegistry bindings) {
        NetworkJS.LOGGER.info("Registering NetworkJS bindings...");
        
        bindings.add("fetch", (FetchFunction) (url, options) -> {
            if (!NetworkJS.isRegistryEnabled()) {
                throw new RuntimeException("NetworkJS registry is disabled! Use /networkjs enable to enable fetch functionality.");
            }
            
            if (options instanceof Map<?, ?> opts) {
                @SuppressWarnings("unchecked")
                Map<String, Object> optMap = (Map<String, Object>) opts;
                FetchOptions javaOptions = new FetchOptions(
                    (String) optMap.getOrDefault("method", "GET"),
                    (Map<String, String>) optMap.getOrDefault("headers", Map.of()),
                    optMap.getOrDefault("body", null)
                );
                return FetchBinding.fetch(url, javaOptions);
            }
            return FetchBinding.fetch(url);
        });

        bindings.add("fetchAsync", (FetchFunction) (url, options) -> {
            if (!NetworkJS.isRegistryEnabled()) {
                throw new RuntimeException("NetworkJS registry is disabled! Use /networkjs enable to enable fetch functionality.");
            }
            
            if (options instanceof Map<?, ?> opts) {
                @SuppressWarnings("unchecked")
                Map<String, Object> optMap = (Map<String, Object>) opts;
                FetchOptions javaOptions = new FetchOptions(
                    (String) optMap.getOrDefault("method", "GET"),
                    (Map<String, String>) optMap.getOrDefault("headers", Map.of()),
                    optMap.getOrDefault("body", null)
                );
                return FetchBinding.fetchAsync(url, javaOptions);
            }
            return FetchBinding.fetchAsync(url);
        });

        bindings.add("FetchBinding", FetchBinding.class);
        bindings.add("FetchOptions", FetchOptions.class);
        bindings.add("FetchResponse", FetchBinding.FetchResponse.class);
        bindings.add("DiscordBot", DiscordBinding.class);
        bindings.add("Server", ServerBinding.class);
        
        NetworkJS.LOGGER.info("NetworkJS bindings registered successfully");
    }

    @FunctionalInterface
    public interface FetchFunction {
        Object call(String url, Object options);
    }
}
