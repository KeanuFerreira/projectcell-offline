package com.example.examplemod.client;

import com.example.examplemod.Config;
import com.example.examplemod.ProjectCell;
import com.example.examplemod.util.EMCFormatUtil;
import java.math.BigInteger;
import javax.annotation.Nullable;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ProjectCell.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = {Dist.CLIENT})
public class EMCDisplay {
   private static final int PADDING_X = 2;
   private static final int PADDING_Y = 2;
   private static BigInteger emc = BigInteger.ZERO;
   private static final BigInteger[] history = new BigInteger[]{BigInteger.ZERO, BigInteger.ZERO};
   private static BigInteger lastEMC = BigInteger.ZERO;
   private static int tick = 0;
   private static int repeatedFailures = 0;

   @Nullable
   private static LocalPlayer getPlayer() {
      return Minecraft.getInstance().player;
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) return;
      if (!Config.CLIENT.spec.isLoaded() || !Config.CLIENT.emcDisplay.get()) {
         return;
      }
      LocalPlayer player = getPlayer();
      tick++;
      if (player != null && tick >= 20) {
         tick = 0;
         IKnowledgeProvider provider = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).resolve().orElse(null);
         if (provider == null) {
            repeatedFailures++;
            if (repeatedFailures < 10) {
               ProjectCell.LOGGER.warn("Failed to get knowledge provider in EMCDisplay");
            } else if (repeatedFailures == 10) {
               ProjectCell.LOGGER.error("Failed to get knowledge provider 10 times, no longer logging.");
            }
            return;
         }
         repeatedFailures = 0;
         emc = provider.getEmc();
         history[1] = history[0];
         history[0] = emc.subtract(lastEMC);
         lastEMC = emc;
      }
   }

   private static void reset() {
      emc = lastEMC = BigInteger.ZERO;
      tick = 0;
      repeatedFailures = 0;
   }

   @SubscribeEvent
   public static void clientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
      reset();
   }

   @SubscribeEvent
   public static void onWorldUnload(LevelEvent.Unload event) {
      reset();
   }

   @Mod.EventBusSubscriber(modid = ProjectCell.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = {Dist.CLIENT})
   public static class Overlay {
      @SubscribeEvent
      public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
         event.registerAboveAll("emc_display", new IGuiOverlay() {
            @Override
            public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
               if (!Config.CLIENT.spec.isLoaded() || !Config.CLIENT.emcDisplay.get()) {
                  return;
               }
               Minecraft mc = Minecraft.getInstance();
               BigInteger avg = history[0].add(history[1]);
               String str = EMCFormatUtil.format(emc);
               if (!avg.equals(BigInteger.ZERO)) {
                  String sign = avg.compareTo(BigInteger.ZERO) > 0 ? "§a+" : "§c-";
                  str = str + " " + sign + EMCFormatUtil.format(avg.abs()) + "/s";
               }
               String text = "EMC: " + str;
               guiGraphics.drawString(mc.font, text, PADDING_X, PADDING_Y, 0xffffff);
            }
         });
      }
   }
}
