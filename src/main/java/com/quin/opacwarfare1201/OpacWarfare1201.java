package com.quin.opacwarfare1201;

import com.mojang.logging.LogUtils;
import com.quin.opacwarfare1201.command.WarCommands;
import com.quin.opacwarfare1201.compat.CBCProtectionEvents;
import com.quin.opacwarfare1201.config.WarConfig;
import com.quin.opacwarfare1201.opac.TerritoryClaimActionListener;
import com.quin.opacwarfare1201.opac.WarAccessOverrider;
import com.quin.opacwarfare1201.war.WarManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.ModLoadingContext;
import org.slf4j.Logger;
import xaero.pac.common.server.api.OpenPACServerAPI;

@Mod(OpacWarfare1201.MODID)
public final class OpacWarfare1201 {
    public static final String MODID = "opac_warfare_1201";
    public static final Logger LOGGER = LogUtils.getLogger();

    public OpacWarfare1201() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, WarConfig.SPEC, "opac-warfare-1201.toml");
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(WarManager.class);
        MinecraftForge.EVENT_BUS.register(CBCProtectionEvents.class);
        LOGGER.info("OPaC Warfare 1.20.1 Port loading; CBC present={}", ModList.get().isLoaded("createbigcannons"));
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        WarCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        WarManager manager = WarManager.get(event.getServer());
        try {
            var claims = OpenPACServerAPI.get(event.getServer()).getServerClaimsManager();
            claims.getChunkAccessOverriderManager().register(new WarAccessOverrider());
            claims.getActionListenerManager().register(new TerritoryClaimActionListener());
            LOGGER.info("Registered OPaC contested-chunk access overrider and territory claim rules");
        } catch (Throwable t) {
            LOGGER.error("Could not register OPaC warfare hooks", t);
        }
        manager.onServerStarted();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        WarManager.clear(event.getServer());
    }
}
