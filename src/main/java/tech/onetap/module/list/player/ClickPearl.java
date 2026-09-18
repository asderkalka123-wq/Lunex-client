package tech.onetap.module.list.player;

import com.google.common.eventbus.Subscribe;
import net.minecraft.item.Items;
import tech.onetap.event.list.EventKeyInput;
import tech.onetap.event.list.EventPlayerSync;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BindSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.util.player.other.InventoryUtil;

@ModuleInformation(moduleName = "Click Pearl", moduleCategory = ModuleCategory.PLAYER)
public class ClickPearl extends Module {
    private final ModeSetting mode = new ModeSetting("Мод", "Обычный", "Обычный", "Легитный");
    private final BindSetting key = new BindSetting("Клавиша броска", -98);

    private boolean pearlUsed;
    private int ticksExisted;

    @Subscribe
    private void onKey(EventKeyInput e) {
        if (e.getAction() == 0) return;
        if (e.getKey() == key.getValue()) {
            if (mode.is("Обычный")) InventoryUtil.swapAndUseHvH(Items.ENDER_PEARL);
            else pearlUsed = true;
        }
    }

    @Subscribe
    private void onPlayerTick(final EventPlayerUpdate ignored) {
        if (mc.player == null) return;
        if (!pearlUsed && ticksExisted > 0) ticksExisted--;
        if (pearlUsed || ticksExisted > 0) mc.player.setSprinting(false);
    }

    @Subscribe
    private void onPlayerSync(final EventPlayerSync ignored) {
        if (mc.player == null || !pearlUsed) return;
        var slotHotbar = InventoryUtil.searchItem(Items.ENDER_PEARL, 0, 9);
        if (slotHotbar != -1) InventoryUtil.swapAndUseLegit(Items.ENDER_PEARL);
        else {
            if (ticksExisted == 0) {
                ticksExisted++;
                return;
            }
            InventoryUtil.swapAndUseLegit(Items.ENDER_PEARL);
            ticksExisted = 2;
        }
        pearlUsed = false;
    }
}