package com.example.examplemod.util;

import com.example.examplemod.Config;
import java.util.UUID;
import javax.annotation.Nullable;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.server.ServerLifecycleHooks;

public class ProjectEUtil {
   private static final boolean TEAM_PROJECTE = ModList.get().isLoaded("teamprojecte");

   @Nullable
   public static ServerPlayer getPlayer(UUID uuid) {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      return server == null ? null : server.getPlayerList().getPlayer(uuid);
   }

   /** The live provider of an online player, or null if they are offline. */
   @Nullable
   public static IKnowledgeProvider getKnowledgeProvider(UUID uuid) {
      ServerPlayer player = getPlayer(uuid);
      return player == null ? null : player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).resolve().orElse(null);
   }

   @Nullable
   public static IKnowledgeProvider getKnowledgeProvider(Player player) {
      return player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).resolve().orElse(null);
   }

   /** Whether offline owners can be served at all (Team ProjectE present and enabled in config). */
   public static boolean offlineAccessAvailable() {
      return TEAM_PROJECTE && Config.SERVER.spec.isLoaded() && Config.SERVER.offlineOwnerAccess.get();
   }

   /**
    * The provider for a cell owner: the live capability when they are online, otherwise their
    * Team ProjectE data. Returns null when neither is available, which makes the cell look empty
    * and refuse transfers, as in the original mod.
    */
   @Nullable
   public static IKnowledgeProvider getOwnerProvider(UUID owner) {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server == null) {
         return null;
      }
      ServerPlayer player = server.getPlayerList().getPlayer(owner);
      if (player != null) {
         IKnowledgeProvider live = getKnowledgeProvider(player);
         if (live != null) {
            return live;
         }
      }
      // Team ProjectE's data is only safe to touch from the server thread.
      if (!offlineAccessAvailable() || !server.isSameThread()) {
         return null;
      }
      return TeamProjectEBridge.getOfflineProvider(owner);
   }

   /** Tells clients about an EMC change: the owner if online, otherwise teammates sharing it. */
   public static void syncEmc(UUID owner, IKnowledgeProvider provider) {
      ServerPlayer player = getPlayer(owner);
      if (player != null) {
         provider.syncEmc(player);
      } else if (offlineAccessAvailable()) {
         TeamProjectEBridge.syncEmc(owner, provider);
      }
   }

   /** Tells clients about newly learned knowledge: the owner if online, otherwise teammates sharing it. */
   public static void syncKnowledgeChange(UUID owner, IKnowledgeProvider provider, ItemInfo learned) {
      ServerPlayer player = getPlayer(owner);
      if (player != null) {
         provider.syncKnowledgeChange(player, learned, true);
      } else if (offlineAccessAvailable()) {
         TeamProjectEBridge.syncKnowledgeChange(owner, provider, learned);
      }
   }
}
