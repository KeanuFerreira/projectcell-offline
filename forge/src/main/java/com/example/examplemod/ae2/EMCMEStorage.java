package com.example.examplemod.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import com.example.examplemod.Config;
import com.example.examplemod.util.ProjectEUtil;
import java.math.BigInteger;
import java.util.UUID;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class EMCMEStorage implements StorageCell {
   private static final long MAX_CELL_COUNT = 2147483647L;
   private final UUID owner;
   private final boolean nbtFilter;
   private final ISaveProvider host;

   private static long limit() {
      return Config.SERVER.spec.isLoaded() && !Config.SERVER.limitItemCount.get() ? Long.MAX_VALUE : 2147483647L;

   }

   private static boolean hasBlockedNbt(ItemStack stack) {
      CompoundTag tag = stack.getTag();
      if (tag == null || tag.isEmpty()) return false;
      if (tag.contains("Enchantments")) return true;
      if (tag.getInt("Damage") > 0) return true;
      if (tag.contains("display")) {
         CompoundTag display = tag.getCompound("display");
         if (display.contains("Name") || display.contains("Lore")) return true;
      }
      return false;
   }

   private static boolean hasBlockedNbt(AEItemKey key) {
      return hasBlockedNbt(key.toStack());
   }

   private static AEItemKey createKey(ItemInfo info) {
      ItemStack stack = info.createStack().copy();
      CompoundTag tag = stack.getTag();
      if (tag != null && tag.contains("Damage", Tag.TAG_ANY_NUMERIC) && tag.getInt("Damage") == 0) {
         tag.remove("Damage");
         if (tag.isEmpty()) stack.setTag(null);
      }
      return AEItemKey.of(stack);
   }

   private static boolean hasNonPersistentNbt(AEItemKey key) {
      ItemStack stack = key.toStack();
      ItemInfo info = ItemInfo.fromStack(stack);
      ItemInfo persistent = IEMCProxy.INSTANCE.getPersistentInfo(info);
      if (ItemStack.isSameItemSameTags(stack, persistent.createStack())) {
         return false;
      }
      CompoundTag tag = stack.getTag();
      if (tag != null && tag.contains("Damage", Tag.TAG_ANY_NUMERIC) && tag.getInt("Damage") == 0) {
         ItemStack cleaned = stack.copy();
         cleaned.getTag().remove("Damage");
         if (cleaned.getTag().isEmpty()) cleaned.setTag(null);
         if (ItemStack.isSameItemSameTags(cleaned, persistent.createStack())) {
            return false;
         }
      }
      return true;
   }

   private static boolean isBlocked(AEItemKey key) {
      return hasBlockedNbt(key) || hasNonPersistentNbt(key);
   }

   public EMCMEStorage(UUID owner, boolean nbtFilter, ISaveProvider host) {
      this.owner = owner;
      this.nbtFilter = nbtFilter;
      this.host = host;
   }

   public CellState getStatus() {
      return CellState.NOT_EMPTY;
   }

   public double getIdleDrain() {
      return 0.0;
   }

   public void persist() {
   }

   public boolean canFitInsideCell() {
      return true;
   }

   public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
      if (what instanceof AEItemKey itemKey && IEMCProxy.INSTANCE.hasValue(itemKey.toStack())) {
         return !this.nbtFilter || !isBlocked(itemKey);
      }
      return false;
   }

   public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
      amount = Math.min(amount, limit());
      if (!(what instanceof AEItemKey itemKey) || amount <= 0L) {
         return 0L;
      }
      ItemStack singleStack = itemKey.toStack(1);
      if (this.nbtFilter && isBlocked(itemKey)) {
         return 0L;
      }
      if (!IEMCProxy.INSTANCE.hasValue(singleStack)) {
         return 0L;
      }
      IKnowledgeProvider provider = ProjectEUtil.getOwnerProvider(this.owner);
      if (provider == null) {
         return 0L;
      }
      // Refuse items that would give no EMC; accepting them would void them.
      long sellValue = IEMCProxy.INSTANCE.getSellValue(singleStack);
      if (sellValue <= 0L) {
         return 0L;
      }
      if (mode == Actionable.MODULATE) {
         BigInteger totalValue = BigInteger.valueOf(sellValue).multiply(BigInteger.valueOf(amount));
         provider.setEmc(provider.getEmc().add(totalValue));
         if (provider.addKnowledge(singleStack)) {
            ProjectEUtil.syncKnowledgeChange(this.owner, provider, IEMCProxy.INSTANCE.getPersistentInfo(ItemInfo.fromStack(singleStack)));
         }
         ProjectEUtil.syncEmc(this.owner, provider);
      }
      return amount;
   }

   public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
      amount = Math.min(amount, limit());
      if (!(what instanceof AEItemKey itemKey) || amount <= 0L) {
         return 0L;
      }
      if (this.nbtFilter && hasBlockedNbt(itemKey)) {
         return 0L;
      }

      ItemStack stack = itemKey.toStack();
      ItemInfo info = ItemInfo.fromStack(stack);
      ItemInfo persistent = IEMCProxy.INSTANCE.getPersistentInfo(info);
      if (!ItemStack.isSameItemSameTags(stack, persistent.createStack())) {
         return 0L;
      }

      IKnowledgeProvider provider = ProjectEUtil.getOwnerProvider(this.owner);
      if (provider == null || !provider.hasKnowledge(stack)) {
         return 0L;
      }
      long itemValue = IEMCProxy.INSTANCE.getValue(stack);
      if (itemValue <= 0L) {
         return 0L;
      }
      BigInteger playerEmc = provider.getEmc();
      BigInteger actualExtract = playerEmc.divide(BigInteger.valueOf(itemValue))
              .min(BigInteger.valueOf(amount))
               .min(BigInteger.valueOf(limit()));
      long extractAmount = actualExtract.longValue();
      if (extractAmount <= 0L) {
         return 0L;
      }
      if (mode == Actionable.MODULATE) {
         BigInteger totalCost = BigInteger.valueOf(itemValue).multiply(BigInteger.valueOf(extractAmount));
         provider.setEmc(playerEmc.subtract(totalCost));
         ProjectEUtil.syncEmc(this.owner, provider);
      }
      return extractAmount;
   }

   public void getAvailableStacks(KeyCounter out) {
      IKnowledgeProvider provider = ProjectEUtil.getOwnerProvider(this.owner);
      if (provider == null) {
         return;
      }
      BigInteger playerEmc = provider.getEmc();
      if (playerEmc.signum() <= 0) {
         return;
      }
      long lim = limit();
      // Most balances fit in a long; dividing longs is much cheaper than BigInteger division.
      boolean fitsLong = playerEmc.bitLength() < 64;
      long emcLong = fitsLong ? playerEmc.longValue() : 0L;
      BigInteger bigLim = BigInteger.valueOf(lim);
      for (KnowledgeKeyCache.Entry entry : KnowledgeKeyCache.get(this.owner, this.nbtFilter, provider, this::listableEntry)) {
         long count;
         if (fitsLong) {
            count = Math.min(emcLong / entry.value(), lim);
         } else {
            count = playerEmc.divide(BigInteger.valueOf(entry.value())).min(bigLim).longValue();
         }
         if (count > 0L) {
            out.add(entry.key(), count);
         }
      }
   }

   /** The cached part of a listing: key and value of a learned item, or null if it can't be listed. */
   private KnowledgeKeyCache.Entry listableEntry(ItemInfo info) {
      long itemValue = IEMCProxy.INSTANCE.getValue(info);
      if (itemValue <= 0L) {
         return null;
      }
      AEItemKey key = createKey(info);
      if (key == null || this.nbtFilter && isBlocked(key)) {
         return null;
      }
      return new KnowledgeKeyCache.Entry(key, itemValue);
   }

   public Component getDescription() {
      return Component.translatable("projectcell.ae2.emc_storage_description");
   }
}
