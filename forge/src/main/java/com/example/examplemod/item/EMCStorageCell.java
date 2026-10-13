package com.example.examplemod.item;

import com.example.examplemod.ProjectCell;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class EMCStorageCell extends Item {
   public EMCStorageCell(Properties properties) {
      super(properties.stacksTo(1));
   }

   @NotNull
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
      ItemStack itemstack = player.getItemInHand(usedHand);
      if (level.isClientSide) {
         return InteractionResultHolder.pass(itemstack);
      } else if (player.isShiftKeyDown()) {
         ProjectCell.setOwnerUUID(itemstack, player.getUUID());
         player.displayClientMessage(Component.translatable("projectcell.message.bound_to_player", player.getDisplayName()), true);
         return InteractionResultHolder.success(itemstack);
      } else {
         boolean current = ProjectCell.getNbtFilter(itemstack);
         ProjectCell.setNbtFilter(itemstack, !current);
         player.displayClientMessage(current
            ? Component.translatable("projectcell.message.nbtFilter.off")
            : Component.translatable("projectcell.message.nbtFilter.on"), true);
         return InteractionResultHolder.success(itemstack);
      }
   }

   public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
      super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);
      boolean nbtFilter = ProjectCell.getNbtFilter(stack);
      tooltipComponents.add(nbtFilter
         ? Component.translatable("projectcell.tooltip.nbtFilter.on").withStyle(ChatFormatting.GREEN)
         : Component.translatable("projectcell.tooltip.nbtFilter.off").withStyle(ChatFormatting.RED));
      if (ProjectCell.hasOwnerUUID(stack)) {
         tooltipComponents.add(Component.translatable("projectcell.tooltip.bound"));
      } else {
         tooltipComponents.add(Component.translatable("projectcell.tooltip.unbound"));
      }
   }
}
