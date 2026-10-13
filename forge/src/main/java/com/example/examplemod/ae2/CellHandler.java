package com.example.examplemod.ae2;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import com.example.examplemod.ProjectCell;
import com.example.examplemod.item.EMCStorageCell;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class CellHandler implements ICellHandler {
   public static final CellHandler INSTANCE = new CellHandler();

   public boolean isCell(ItemStack itemStack) {
      return itemStack.getItem() instanceof EMCStorageCell;
   }

   @Nullable
   public StorageCell getCellInventory(ItemStack itemStack, @Nullable ISaveProvider host) {
      if (!this.isCell(itemStack)) {
         return null;
      } else {
          UUID owner = ProjectCell.getOwnerUUID(itemStack);
         if (owner == null) return null;
         boolean nbtFilter = ProjectCell.getNbtFilter(itemStack);
         return new EMCMEStorage(owner, nbtFilter, host);
      }
   }
}
