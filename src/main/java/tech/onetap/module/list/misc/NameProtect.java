package tech.onetap.module.list.misc;

import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.util.friend.Friend;
import tech.onetap.util.friend.FriendRepository;

@ModuleInformation(moduleName = "Streamer Mode", moduleCategory = ModuleCategory.MISC)
public class NameProtect extends Module {

    public final BooleanSetting hideFriends = new BooleanSetting("Скрыть друзей", false);

    public String getCustomName() {
        return isEnabled() ? "Protected" : mc.player.getNameForScoreboard();
    }

    public String getCustomName(String originalName) {
        if (!isEnabled() || mc.player == null) {
            return originalName;
        }

        String me = mc.player.getNameForScoreboard();
        if (originalName.contains(me)) {
            return originalName.replace(me, "Protected");
        }

        if (hideFriends.getValue()) {
            var friends = FriendRepository.getFriends();
            for (Friend friend : friends) {
                if (originalName.contains(friend.name())) {
                    return originalName.replace(friend.name(), "Protected");
                }
            }
        }

        return originalName;
    }
}