package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import tech.onetap.event.list.EventPacket;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;

@ModuleInformation(moduleName = "Velocity", moduleCategory = ModuleCategory.COMBAT)
public class Velocity extends Module {
    @Subscribe
    private void onPacket(EventPacket e) {
        if (e.getPacket() instanceof EntityVelocityUpdateS2CPacket packet) {
            if (packet.getEntityId() != mc.player.getId()) return;

            e.cancelEvent();
        }
    }
}