package com.example.examplemod.util;

import cn.leomc.teamprojecte.TPTeam;
import cn.leomc.teamprojecte.TeamKnowledgeProvider;
import cn.leomc.teamprojecte.TeamProjectE;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import net.minecraft.server.level.ServerPlayer;

/**
 * Access to Team ProjectE. Only loaded when the mod is present.
 *
 * Team ProjectE keeps every player's EMC and knowledge in the world's "teamprojecte" SavedData
 * instead of the player capability. A provider built from a UUID reads and writes that same data,
 * so it stays valid while the player is offline and needs no merging when they return.
 */
final class TeamProjectEBridge {
   private TeamProjectEBridge() {
   }

   /** A provider for an offline owner, or null if the owner has no Team ProjectE data yet. */
   @Nullable
   static IKnowledgeProvider getOfflineProvider(UUID owner) {
      // Don't create an empty team for a UUID that has never been seen.
      if (TPTeam.getTeamByMember(owner) == null) {
         return null;
      }
      // A new provider per call: providers memoize their team, and team membership can change.
      return new TeamKnowledgeProvider(owner);
   }

   /** Sends an offline owner's EMC change to online teammates who share it. */
   static void syncEmc(UUID owner, IKnowledgeProvider provider) {
      TPTeam team = TPTeam.getTeamByMember(owner);
      if (team != null && team.isSharingEMC()) {
         ServerPlayer member = firstOnlineMember(owner);
         if (member != null) {
            // Broadcasts to every online team member.
            provider.syncEmc(member);
         }
      }
   }

   /** Sends knowledge an offline owner learned to online teammates who share it. */
   static void syncKnowledgeChange(UUID owner, IKnowledgeProvider provider, ItemInfo learned) {
      TPTeam team = TPTeam.getTeamByMember(owner);
      if (team != null && team.isSharingKnowledge()) {
         ServerPlayer member = firstOnlineMember(owner);
         if (member != null) {
            provider.syncKnowledgeChange(member, learned, true);
         }
      }
   }

   @Nullable
   private static ServerPlayer firstOnlineMember(UUID owner) {
      List<ServerPlayer> online = TeamProjectE.getOnlineTeamMembers(owner);
      return online.isEmpty() ? null : online.get(0);
   }
}
