package com.example.examplemod;

import appeng.api.storage.StorageCells;
import com.example.examplemod.ae2.CellHandler;
import com.example.examplemod.item.EMCStorageCell;
import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;

@Mod(ProjectCell.MODID)
public class ProjectCell {
   public static final String MODID = "projectcell";
   public static final Logger LOGGER = LogUtils.getLogger();
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
   public static final RegistryObject<EMCStorageCell> EMC_STORAGE_CELL = ITEMS.register("emc_storage_cell", () -> new EMCStorageCell(new Item.Properties()));

   private static final String TAG_OWNER_UUID = "owner_uuid";
   private static final String TAG_NBT_FILTER = "nbt_filter";

   public ProjectCell() {
      IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

      Config.register();

      modEventBus.addListener(this::commonSetup);
      modEventBus.addListener(this::addCreative);
      ITEMS.register(modEventBus);
   }

   public static ResourceLocation rl(String path) {
      return new ResourceLocation(MODID, path);
   }

   public static UUID getOwnerUUID(net.minecraft.world.item.ItemStack stack) {
      CompoundTag tag = stack.getTag();
      if (tag != null && tag.hasUUID(TAG_OWNER_UUID)) {
         return tag.getUUID(TAG_OWNER_UUID);
      }
      return null;
   }

   public static void setOwnerUUID(net.minecraft.world.item.ItemStack stack, UUID uuid) {
      stack.getOrCreateTag().putUUID(TAG_OWNER_UUID, uuid);
   }

   public static boolean hasOwnerUUID(net.minecraft.world.item.ItemStack stack) {
      return stack.hasTag() && stack.getTag().contains(TAG_OWNER_UUID);
   }

   public static boolean getNbtFilter(net.minecraft.world.item.ItemStack stack) {
      return stack.hasTag() && stack.getTag().getBoolean(TAG_NBT_FILTER);
   }

   public static void setNbtFilter(net.minecraft.world.item.ItemStack stack, boolean value) {
      stack.getOrCreateTag().putBoolean(TAG_NBT_FILTER, value);
   }

   private void commonSetup(FMLCommonSetupEvent event) {
      event.enqueueWork(() -> StorageCells.addCellHandler(CellHandler.INSTANCE));
   }

   private void addCreative(BuildCreativeModeTabContentsEvent event) {
      if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
         event.accept(EMC_STORAGE_CELL);
      }
   }
}
