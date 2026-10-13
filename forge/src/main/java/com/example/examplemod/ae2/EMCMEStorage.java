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
import java.util.Iterator;
import java.util.UUID;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
      IKnowledgeProvider provider = ProjectEUtil.getKnowledgeProvider(this.owner);
      if (provider == null) {
         return 0L;
      }
      if (mode == Actionable.MODULATE) {
         long sellValue = IEMCProxy.INSTANCE.getSellValue(singleStack);
         BigInteger totalValue = BigInteger.valueOf(sellValue).multiply(BigInteger.valueOf(amount));
         provider.setEmc(provider.getEmc().add(totalValue));
         ServerPlayer player = ProjectEUtil.getPlayer(this.owner);
         if (player != null) {
            if (provider.addKnowledge(singleStack)) {
               provider.syncKnowledgeChange(player, IEMCProxy.INSTANCE.getPersistentInfo(ItemInfo.fromStack(singleStack)), true);
            }
            provider.syncEmc(player);
         }
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

      IKnowledgeProvider provider = ProjectEUtil.getKnowledgeProvider(this.owner);
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
         ServerPlayer player = ProjectEUtil.getPlayer(this.owner);
         if (player != null) {
            provider.syncEmc(player);
         }
      }
      return extractAmount;
   }

   public void getAvailableStacks(KeyCounter out) {
      IKnowledgeProvider provider = ProjectEUtil.getKnowledgeProvider(this.owner);
      if (provider != null) {
         BigInteger playerEmc = provider.getEmc();
         long lim = limit();
			Iterator var6 = provider.getKnowledge().iterator();

			while(true) {
				BigInteger count;
				AEItemKey key;
				do {
					do {
						ItemInfo info;
						do {
							long itemValue;
							do {
								if (!var6.hasNext()) {
									return;
								}

								info = (ItemInfo)var6.next();
								itemValue = IEMCProxy.INSTANCE.getValue(info);
							} while(itemValue <= 0L);

							count = playerEmc.divide(BigInteger.valueOf(itemValue));
						} while(count.compareTo(BigInteger.ZERO) <= 0);

						key = createKey(info);
					} while(key == null);
				} while(this.nbtFilter && isBlocked(key));

               out.add(key, count.min(BigInteger.valueOf(lim)).longValue());
            }
      }
   }

   public Component getDescription() {
      return Component.translatable("projectcell.ae2.emc_storage_description");
   }
}
