package dev.marigold.illustrations;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(Illustrations.ID)
public final class Illustrations {
    public static final String ID="scholar_illustrations";
    public static final Logger LOG=LogUtils.getLogger();
    public Illustrations(IEventBus bus) {
        bus.addListener(Network::register);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e)->ServerArtwork.logout(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e)->ServerArtwork.clear());
    }
}
