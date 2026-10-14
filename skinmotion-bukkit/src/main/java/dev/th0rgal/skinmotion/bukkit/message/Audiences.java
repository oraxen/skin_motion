package dev.th0rgal.skinmotion.bukkit.message;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves Adventure audiences for command senders.
 * Paper senders are native audiences; the shaded adventure-platform-bukkit is only used on servers
 * without native Adventure (Spigot), since its facets silently drop messages on recent Paper builds.
 */
public final class Audiences {

    private static final boolean NATIVE = Audience.class.isAssignableFrom(CommandSender.class);

    @Nullable
    private final BukkitAudiences platform;

    public Audiences(@NotNull Plugin plugin) {
        this.platform = NATIVE ? null : BukkitAudiences.create(plugin);
    }

    public static boolean isNative() {
        return NATIVE;
    }

    @NotNull
    public Audience sender(@NotNull CommandSender sender) {
        if (platform == null) {
            return (Audience) sender;
        }
        return platform.sender(sender);
    }

    public void close() {
        if (platform != null) {
            platform.close();
        }
    }
}
