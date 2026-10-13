package com.example.examplemod;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public class Config {
   public static final Server SERVER = new Server();
   public static final Client CLIENT = new Client();

   public static void register() {
      ModLoadingContext ctx = ModLoadingContext.get();
      ctx.registerConfig(ModConfig.Type.CLIENT, CLIENT.spec, "projectcell/client.toml");
      ctx.registerConfig(ModConfig.Type.COMMON, SERVER.spec, "projectcell/server.toml");
   }

   public static class Server {
      public final ForgeConfigSpec spec;
      public final ForgeConfigSpec.BooleanValue limitItemCount;
      public final ForgeConfigSpec.BooleanValue offlineOwnerAccess;

      Server() {
         ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
         limitItemCount = builder
            .comment("Limit the maximum item count per type in the EMC storage cell to 2147483647.")
            .translation("projectcell.configuration.limitItemCount")
            .define("limitItemCount", true);
         offlineOwnerAccess = builder
            .comment("Keep EMC Storage Cells working while their owner is offline.",
               "Requires Team ProjectE, which stores EMC and knowledge in the world save. Without it this has no effect.")
            .translation("projectcell.configuration.offlineOwnerAccess")
            .define("offlineOwnerAccess", true);
         spec = builder.build();
      }
   }

   public static class Client {
      public final ForgeConfigSpec spec;
      public final ForgeConfigSpec.BooleanValue emcDisplay;
      public final ForgeConfigSpec.BooleanValue enableLearnedTooltip;
      public final ForgeConfigSpec.BooleanValue formatEMC;
      public final ForgeConfigSpec.BooleanValue emcShortNames;

      Client() {
         ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
         emcDisplay = builder
            .comment("Displays your current EMC and gained EMC/s in the top-left corner.")
            .translation("projectcell.configuration.emcDisplay")
            .define("emcDisplay", false);
         enableLearnedTooltip = builder
            .comment("Shows learned (✓) or unlearned (✗) indicator on item tooltips.")
            .translation("projectcell.configuration.enableLearnedTooltip")
            .define("enableLearnedTooltip", false);
         formatEMC = builder
            .comment("Enable EMC value formatting with suffixes (M/B/T...).")
            .translation("projectcell.configuration.formatEMC")
            .define("formatEMC", true);
         emcShortNames = builder
            .comment("Use short unit names (M/B/T) instead of long names (Million/Billion/Trillion).")
            .translation("projectcell.configuration.emcShortNames")
            .define("emcShortNames", false);
         spec = builder.build();
      }
   }
}
