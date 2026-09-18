package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import tech.onetap.event.list.EventAttack;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;

@ModuleInformation(moduleName = "Criticals", moduleCategory = ModuleCategory.COMBAT)
public class Criticals extends Module {
    @Subscribe
    private void onAttack(EventAttack e) {
        if (mc.player.fallDistance == 0 && !mc.player.isOnGround()) {
            mc.player.fallDistance = 0.001f;
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY() - 0.0000999999999, mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), false, mc.player.horizontalCollision));
        }
    }
}