package tech.onetap.module.list.player;

import com.google.common.eventbus.Subscribe;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import tech.onetap.event.list.EventKeyInput;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BindSetting;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.player.other.InventoryUtil;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

@ModuleInformation(moduleName = "Wind Charge", moduleCategory = ModuleCategory.PLAYER)
public class WindCharge extends Module {
    private final BindSetting key = new BindSetting("Клавиша", -97);
    private final SliderSetting rotateSpeed = new SliderSetting("Скорость поворота", 180.0, 50.0, 360.0, 10.0);
    private final BooleanSetting autoDisable = new BooleanSetting("Авто-выкл", false);

    private enum State { IDLE, ROTATING, THROWING }
    private State state = State.IDLE;
    private int tickCount;
    private int previousSlot;
    private int windChargeSlot = -1;
    private boolean hasMace;

    @Subscribe
    private void onKey(EventKeyInput e) {
        if (e.getAction() == 0) return;
        if (e.getKey() != key.getValue()) return;
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        windChargeSlot = InventoryUtil.searchItem(Items.WIND_CHARGE);
        if (windChargeSlot == -1) return;

        previousSlot = mc.player.getInventory().selectedSlot;
        hasMace = InventoryUtil.searchItem(Items.MACE) != -1;

        if (hasMace) {
            int maceSlot = InventoryUtil.searchItem(Items.MACE);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(maceSlot));
        }

        Rotation target = new Rotation(mc.player.getYaw(), 90.0f);
        RotationComponent.update(target, (float) rotateSpeed.getValue(), (float) rotateSpeed.getValue(), 360.0f, 360.0f, 20, 2, false);

        state = State.ROTATING;
        tickCount = 0;
    }

    @Subscribe
    private void onTick(EventTick e) {
        if (mc.player == null || mc.getNetworkHandler() == null || state == State.IDLE) return;

        switch (state) {
            case ROTATING -> {
                if (mc.player.getPitch() >= 85.0f) {
                    mc.interactionManager.clickSlot(0, windChargeSlot, 1, SlotActionType.SWAP, mc.player);
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(1));
                    mc.interactionManager.interactItem(mc.player, net.minecraft.util.Hand.MAIN_HAND);

                    state = State.THROWING;
                    tickCount = 0;
                }
            }
            case THROWING -> {
                tickCount++;
                if (tickCount >= 1) {
                    mc.interactionManager.clickSlot(0, windChargeSlot, 1, SlotActionType.SWAP, mc.player);
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
                    mc.player.getInventory().selectedSlot = previousSlot;

                    state = State.IDLE;
                    if (autoDisable.getValue()) setEnabled(false);
                }
            }
            default -> {}
        }
    }
}
