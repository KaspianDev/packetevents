/*
 * MIT License
 *
 * Copyright (c) 2020 retrooper
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.github.retrooper.packetevents;

import io.github.retrooper.packetevents.event.impl.PostPlayerInjectEvent;
import io.github.retrooper.packetevents.event.manager.EventManager;
import io.github.retrooper.packetevents.event.manager.PEEventManager;
import io.github.retrooper.packetevents.exceptions.PacketEventsLoadFailureException;
import io.github.retrooper.packetevents.injector.GlobalChannelInjector;
import io.github.retrooper.packetevents.packettype.PacketTypeClasses;
import io.github.retrooper.packetevents.packetwrappers.WrappedPacket;
import io.github.retrooper.packetevents.processor.PacketProcessorInternal;
import io.github.retrooper.packetevents.settings.PacketEventsSettings;
import io.github.retrooper.packetevents.updatechecker.UpdateChecker;
import io.github.retrooper.packetevents.utils.entityfinder.EntityFinderUtils;
import io.github.retrooper.packetevents.utils.netty.bytebuf.ByteBufUtil;
import io.github.retrooper.packetevents.utils.netty.bytebuf.ByteBufUtil_7;
import io.github.retrooper.packetevents.utils.netty.bytebuf.ByteBufUtil_8;
import io.github.retrooper.packetevents.utils.nms.NMSUtils;
import io.github.retrooper.packetevents.utils.player.ClientVersion;
import io.github.retrooper.packetevents.utils.player.PlayerUtils;
import io.github.retrooper.packetevents.utils.server.ServerUtils;
import io.github.retrooper.packetevents.utils.server.ServerVersion;
import io.github.retrooper.packetevents.utils.version.PEVersion;
import io.github.retrooper.packetevents.utils.versionlookup.VersionLookupUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import java.net.InetSocketAddress;
import java.util.UUID;

public final class PacketEvents implements Listener, EventManager {
    //TODO finish unfinished wrappers. make a pre release, remove getTarget in use entity in example
    private static PacketEvents instance;
    private final PEVersion version = new PEVersion(1, 7, 9, 5);
    private final EventManager eventManager = new PEEventManager();
    private final PlayerUtils playerUtils = new PlayerUtils();
    private final ServerUtils serverUtils = new ServerUtils();
    private final UpdateChecker updateChecker = new UpdateChecker();
    private static Plugin plugin;
    public static String handlerName;
    public PacketProcessorInternal packetProcessorInternal;
    public GlobalChannelInjector injector;
    private boolean loading, loaded, initialized, initializing, terminating;
    private PacketEventsSettings settings = new PacketEventsSettings();
    private ByteBufUtil byteBufUtil;

    public static PacketEvents create(final Plugin plugin) {
        if (!Bukkit.getServicesManager().isProvidedFor(PacketEvents.class)) {
            instance = new PacketEvents();
            Bukkit.getServicesManager().register(PacketEvents.class, instance,
                    plugin, ServicePriority.Normal);
            PacketEvents.plugin = plugin;
            return instance;
        }
        return instance = Bukkit.getServicesManager().load(PacketEvents.class);
    }

    public static PacketEvents get() {
        return instance;
    }

    @Deprecated
    public static PacketEvents getAPI() {
        return get();
    }


    public void load() {
        if (!loaded && !loading) {
            loading = true;
            ServerVersion version = ServerVersion.getVersion();
            WrappedPacket.version = version;
            NMSUtils.version = version;
            EntityFinderUtils.version = version;
            handlerName = "pe-" + plugin.getName();

            try {
                NMSUtils.load();

                //Load all classes
                PacketTypeClasses.load();

                EntityFinderUtils.load();
            } catch (Exception ex) {
                loading = false;
                throw new PacketEventsLoadFailureException(ex);
            }

            byteBufUtil = NMSUtils.legacyNettyImportMode ? new ByteBufUtil_7() : new ByteBufUtil_8();

            injector = new GlobalChannelInjector();
            injector.inject();

            loaded = true;
            loading = false;
        }
    }

    public void loadSettings(PacketEventsSettings settings) {
        this.settings = settings;
    }

    public void init(final Plugin plugin) {
        init(plugin, settings);
    }

    public void init(final Plugin pl, PacketEventsSettings packetEventsSettings) {
        load();
        if (!initialized && !initializing) {
            initializing = true;
            settings = packetEventsSettings;
            settings.lock();

            Bukkit.getPluginManager().registerEvents(this, plugin);

            packetProcessorInternal = new PacketProcessorInternal();

            for (final Player p : Bukkit.getOnlinePlayers()) {
                getPlayerUtils().injectPlayer(p);
            }

            if (settings.shouldCheckForUpdates()) {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        PacketEvents.get().getPlugin().getLogger().info("[packetevents] Checking for an update, please wait...");
                        UpdateChecker.UpdateCheckerStatus status = updateChecker.checkForUpdate();
                        int seconds = 5;
                        for (int i = 0; i < 5; i++) {
                            if (status == UpdateChecker.UpdateCheckerStatus.FAILED) {
                                PacketEvents.get().getPlugin().getLogger().severe("[packetevents] Checking for an update again in " + seconds + " seconds...");
                                try {
                                    Thread.sleep(seconds * 1000L);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }

                                seconds *= 2;

                                status = updateChecker.checkForUpdate();

                                if (i == 4) {
                                    PacketEvents.get().getPlugin().getLogger().severe("[packetevents] PacketEvents failed to check for an update. No longer retrying.");
                                    break;
                                }
                            } else {
                                break;
                            }

                        }

                    }
                }, "PacketEvents-update-check-thread");
                thread.start();
            }
            initialized = true;
            initializing = false;
        }
    }

    public void terminate() {
        if (initialized && !terminating) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                injector.ejectPlayer(player);
            }

            injector.eject();

            getEventManager().unregisterAllListeners();
            initialized = false;
            terminating = false;
        }
    }

    /**
     * Use {@link #terminate()}. This is deprecated
     *
     * @deprecated "Stop" might be misleading and "terminate" sounds better I guess...
     */
    @Deprecated
    public void stop() {
        terminate();
    }

    public boolean hasLoaded() {
        return loaded;
    }

    public boolean isLoading() {
        return loading;
    }

    public boolean isInitializing() {
        return initializing;
    }

    @Deprecated
    public boolean isStopping() {
        return terminating;
    }

    public boolean isTerminating() {
        return terminating;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    /**
     * Get the PacketEvents settings.
     *
     * @return Configure some settings for the API
     */
    public PacketEventsSettings getSettings() {
        return settings;
    }

    public PEVersion getVersion() {
        return version;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public PlayerUtils getPlayerUtils() {
        return playerUtils;
    }

    public ServerUtils getServerUtils() {
        return serverUtils;
    }

    public ByteBufUtil getByteBufUtil() {
        return byteBufUtil;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(final PlayerLoginEvent e) {
        final Player player = e.getPlayer();
        if (!getSettings().shouldUseCompatibilityInjector()) {
            injector.injectPlayer(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(final PlayerJoinEvent e) {
        Player player = e.getPlayer();
        final InetSocketAddress address = player.getAddress();
        boolean dependencyAvailable = VersionLookupUtils.isDependencyAvailable();
        PacketEvents.get().getPlayerUtils().loginTime.put(player.getUniqueId(), System.currentTimeMillis());
        if (dependencyAvailable) {
            PacketEvents.get().getPlayerUtils().clientVersionsMap.put(player.getAddress(), ClientVersion.TEMP_UNRESOLVED);
            Bukkit.getScheduler().runTaskLaterAsynchronously(getPlugin(), new Runnable() {
                @Override
                public void run() {
                    int protocolVersion;
                    try {
                        protocolVersion = VersionLookupUtils.getProtocolVersion(player);
                    } catch (Exception ex) {
                        protocolVersion = -1;
                    }
                    if (protocolVersion != -1) {
                        ClientVersion version = ClientVersion.getClientVersion(protocolVersion);
                        PacketEvents.get().getPlayerUtils().clientVersionsMap.put(address, version);
                    }

                    PacketEvents.get().getEventManager().callEvent(new PostPlayerInjectEvent(player, true));
                }
            }, 1L);
        }

        boolean shouldInject = getSettings().shouldUseCompatibilityInjector() || !(injector.hasInjected(e.getPlayer()));
        //Inject now if we are using the compatibility-injector or inject if the early injector failed to inject them.
        if (shouldInject) {
            injector.injectPlayer(player);
        }

        //Dependency isn't available, we can already call the post player inject event.
        if (!dependencyAvailable) {
            PacketEvents.get().getEventManager().callEvent(new PostPlayerInjectEvent(e.getPlayer(), false));
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(final PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        injector.ejectPlayer(e.getPlayer());
        instance.getPlayerUtils().loginTime.remove(uuid);
        instance.getPlayerUtils().playerPingMap.remove(uuid);
        instance.getPlayerUtils().playerSmoothedPingMap.remove(uuid);
    }
}
