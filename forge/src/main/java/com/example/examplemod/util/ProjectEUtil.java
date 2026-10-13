package com.example.examplemod.util;

import java.util.UUID;
import javax.annotation.Nullable;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.server.ServerLifecycleHooks;

public class ProjectEUtil {
   @Nullable
   public static ServerPlayer getPlayer(UUID uuid) {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      return server == null ? null : server.getPlayerList().getPlayer(uuid);
   }

   @Nullable
   public static IKnowledgeProvider getKnowledgeProvider(UUID uuid) {
      ServerPlayer player = getPlayer(uuid);
      return player == null ? null : player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).resolve().orElse(null);
   }

   @Nullable
   public static IKnowledgeProvider getKnowledgeProvider(Player player) {
      return player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).resolve().orElse(null);
   }
}
