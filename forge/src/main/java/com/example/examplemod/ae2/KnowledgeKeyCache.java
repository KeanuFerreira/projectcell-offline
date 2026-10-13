package com.example.examplemod.ae2;

import appeng.api.stacks.AEItemKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;

/**
 * Per owner and NBT-filter setting: the AE2 key and EMC value of every listable learned item.
 *
 * AE2 asks a cell for its contents every tick while anything watches the network. Looking up EMC
 * values, building keys and checking NBT for every learned item is most of that cost, and it only
 * changes when the knowledge or the EMC mapping changes. Amounts are still computed from the live
 * balance on every call.
 */
public final class KnowledgeKeyCache {
   record Entry(AEItemKey key, long value) {
   }

   private record CacheKey(UUID owner, boolean nbtFilter) {
   }

   private record Cached(int generation, boolean fullKnowledge, int size, long hash, List<Entry> entries) {
   }

   private static final Map<CacheKey, Cached> CACHE = new HashMap<>();
   private static int generation;

   private KnowledgeKeyCache() {
   }

   /** Drops everything, e.g. after EMC values were remapped. */
   public static synchronized void invalidateAll() {
      generation++;
      CACHE.clear();
   }

   /**
    * The entries for the provider's current knowledge. Rebuilt when the knowledge set (size and
    * order-independent hash), the full-knowledge flag or the EMC mapping generation changed.
    */
   static synchronized List<Entry> get(UUID owner, boolean nbtFilter, IKnowledgeProvider provider, Function<ItemInfo, Entry> builder) {
      Set<ItemInfo> knowledge = provider.getKnowledge();
      boolean full = provider.hasFullKnowledge();
      long hash = 0L;
      for (ItemInfo info : knowledge) {
         hash += info.hashCode();
      }
      CacheKey key = new CacheKey(owner, nbtFilter);
      Cached cached = CACHE.get(key);
      if (cached != null && cached.generation == generation && cached.fullKnowledge == full
            && cached.size == knowledge.size() && cached.hash == hash) {
         return cached.entries;
      }
      List<Entry> entries = new ArrayList<>(knowledge.size());
      for (ItemInfo info : knowledge) {
         Entry entry = builder.apply(info);
         if (entry != null) {
            entries.add(entry);
         }
      }
      List<Entry> result = List.copyOf(entries);
      CACHE.put(key, new Cached(generation, full, knowledge.size(), hash, result));
      return result;
   }
}
