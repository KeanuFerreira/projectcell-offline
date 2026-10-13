package com.example.examplemod.events;

import com.example.examplemod.Config;
import com.example.examplemod.ProjectCell;
import com.example.examplemod.util.ProjectEUtil;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.text.PELang;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ProjectCell.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = {Dist.CLIENT})
public class ItemTooltipEvents {
   @SubscribeEvent(priority = EventPriority.LOW)
   public static void itemTooltipEvent(ItemTooltipEvent event) {
      ItemStack stack = event.getItemStack();
      if (stack.isEmpty() || event.getEntity() == null || event.getEntity().isDeadOrDying()) {
         return;
      }
      if (!Config.CLIENT.enableLearnedTooltip.get()) {
         return;
      }
      if (!IEMCProxy.INSTANCE.hasValue(stack)) {
         return;
      }

      IKnowledgeProvider provider = ProjectEUtil.getKnowledgeProvider(event.getEntity());
      if (provider == null) {
         return;
      }

      boolean hasKnowledge = provider.hasKnowledge(ItemInfo.fromStack(stack));
      long value = IEMCProxy.INSTANCE.getValue(stack);
      String emcText = EMCHelper.getEmcTextComponent(value, 1).getString();
      String peTransmutableText = I18n.get(PELang.EMC_HAS_KNOWLEDGE.getTranslationKey());
      int index = -1;
      int peTransmutableIndex = -1;

      for (int i = 0; i < event.getToolTip().size(); i++) {
         String line = event.getToolTip().get(i).getString();
         if (line.equals(emcText)) {
            index = i;
         } else if (line.equals(peTransmutableText)) {
            peTransmutableIndex = i;
         }
      }

      if (peTransmutableIndex != -1) {
         event.getToolTip().remove(peTransmutableIndex);
         if (peTransmutableIndex < index) {
            index--;
         }
      }

      if (index != -1) {
         MutableComponent existing = event.getToolTip().get(index).copy();
         existing.append(Component.literal(" (").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)));
         existing.append(hasKnowledge
               ? Component.literal("✓").setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN))
               : Component.literal("✗").setStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
         existing.append(Component.literal(")").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)));
         event.getToolTip().set(index, existing);
      } else {
         event.getToolTip().add(hasKnowledge
               ? Component.translatable("projectcell.tooltip.learned").withStyle(ChatFormatting.GREEN)
               : Component.translatable("projectcell.tooltip.not_learned").withStyle(ChatFormatting.RED));
      }
   }
}
