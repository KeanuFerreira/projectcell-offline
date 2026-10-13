package pctest;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.storage.DriveBlockEntity;
import cn.leomc.teamprojecte.TPTeam;
import cn.leomc.teamprojecte.TeamKnowledgeProvider;
import com.example.examplemod.ProjectCell;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import cool.furry.mc.forge.projectexpansion.block.entity.BlockEntityPowerFlower;
import cool.furry.mc.forge.projectexpansion.config.Config;
import cool.furry.mc.forge.projectexpansion.util.Matter;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * Console commands that drive the offline-owner test matrix on a dedicated server.
 * Every result line starts with "PCTEST" so the driver script can parse the log.
 */
@Mod("pctest")
public class PcTest {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Map<String, ServerPlayer> MOCKS = new HashMap<>();
   private static final List<BooleanSupplier> TASKS = new ArrayList<>();

   public PcTest() {
      MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
      MinecraftForge.EVENT_BUS.addListener(this::serverTick);
   }

   private static void out(CommandSourceStack src, String msg) {
      LOGGER.info("PCTEST " + msg);
   }

   private static UUID uuid(String name) {
      return UUIDUtil.createOfflinePlayerUUID(name);
   }

   /** Reads Team ProjectE data directly; null if the player has no team. */
   private static IKnowledgeProvider data(String name) {
      UUID id = uuid(name);
      return TPTeam.getTeamByMember(id) == null ? null : new TeamKnowledgeProvider(id);
   }

   private static BigInteger emc(String name) {
      IKnowledgeProvider p = data(name);
      return p == null ? null : p.getEmc();
   }

   private static IGrid grid(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
      if (ctx.getSource().getLevel().getBlockEntity(pos) instanceof DriveBlockEntity drive) {
         IGrid grid = drive.getMainNode().getGrid();
         if (grid != null) {
            return grid;
         }
      }
      throw new IllegalStateException("no powered drive grid at " + pos);
   }

   private static AEItemKey key(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      return AEItemKey.of(ItemArgument.getItem(ctx, "item").createItemStack(1, false));
   }

   private static String keyName(AEItemKey key) {
      return key.toStack().toString() + (key.hasTag() ? key.getTag().toString() : "");
   }

   private static int run(CommandContext<CommandSourceStack> ctx, Body body) {
      try {
         body.run(ctx);
      } catch (Exception e) {
         LOGGER.error("PCTEST ERROR " + e, e);
      }
      return 1;
   }

   interface Body {
      void run(CommandContext<CommandSourceStack> ctx) throws Exception;
   }

   private void registerCommands(RegisterCommandsEvent event) {
      CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
      CommandBuildContext build = event.getBuildContext();
      d.register(Commands.literal("pctest").requires(s -> s.hasPermission(4))
         .then(Commands.literal("login").then(Commands.argument("name", StringArgumentType.word()).executes(c -> run(c, PcTest::login))))
         .then(Commands.literal("logout").then(Commands.argument("name", StringArgumentType.word()).executes(c -> run(c, PcTest::logout))))
         .then(Commands.literal("respawn").then(Commands.argument("name", StringArgumentType.word()).executes(c -> run(c, PcTest::respawn))))
         .then(Commands.literal("emc").then(Commands.argument("name", StringArgumentType.word()).executes(c -> run(c, PcTest::printEmc))))
         .then(Commands.literal("setemc").then(Commands.argument("name", StringArgumentType.word())
            .then(Commands.argument("value", StringArgumentType.word()).executes(c -> run(c, PcTest::setEmc)))))
         .then(Commands.literal("fullknowledge").then(Commands.argument("name", StringArgumentType.word())
            .then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> run(c, PcTest::fullKnowledge)))))
         .then(Commands.literal("hasteam").then(Commands.argument("name", StringArgumentType.word()).executes(c -> run(c, PcTest::hasTeam))))
         .then(Commands.literal("knows").then(Commands.argument("name", StringArgumentType.word())
            .then(Commands.argument("item", ItemArgument.item(build)).executes(c -> run(c, PcTest::knows)))))
         .then(Commands.literal("value").then(Commands.argument("item", ItemArgument.item(build)).executes(c -> run(c, PcTest::value))))
         .then(Commands.literal("putcell").then(Commands.argument("pos", BlockPosArgument.blockPos())
            .then(Commands.argument("slot", IntegerArgumentType.integer(0, 9))
               .then(Commands.argument("name", StringArgumentType.word())
                  .then(Commands.argument("filter", BoolArgumentType.bool()).executes(c -> run(c, PcTest::putCell)))))))
         .then(Commands.literal("takecell").then(Commands.argument("pos", BlockPosArgument.blockPos())
            .then(Commands.argument("slot", IntegerArgumentType.integer(0, 9)).executes(c -> run(c, PcTest::takeCell)))))
         .then(Commands.literal("count").then(Commands.argument("pos", BlockPosArgument.blockPos())
            .then(Commands.argument("item", ItemArgument.item(build)).executes(c -> run(c, PcTest::count)))))
         .then(Commands.literal("insert").then(Commands.argument("pos", BlockPosArgument.blockPos())
            .then(Commands.argument("item", ItemArgument.item(build))
               .then(Commands.argument("n", LongArgumentType.longArg(1)).executes(c -> run(c, PcTest::insert))))))
         .then(Commands.literal("extract").then(Commands.argument("pos", BlockPosArgument.blockPos())
            .then(Commands.argument("item", ItemArgument.item(build))
               .then(Commands.argument("n", LongArgumentType.longArg(1)).executes(c -> run(c, PcTest::extract))))))
         .then(Commands.literal("flower")
            .then(Commands.literal("place").then(Commands.argument("pos", BlockPosArgument.blockPos())
               .then(Commands.argument("name", StringArgumentType.word()).executes(c -> run(c, PcTest::placeFlower)))))
            .then(Commands.literal("info").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> run(c, PcTest::flowerInfo))))
            .then(Commands.literal("logincheck").then(Commands.argument("pos", BlockPosArgument.blockPos())
               .then(Commands.argument("name", StringArgumentType.word()).executes(c -> run(c, PcTest::flowerLoginCheck)))))));
   }

   private static void login(CommandContext<CommandSourceStack> c) {
      String name = StringArgumentType.getString(c, "name");
      MinecraftServer server = c.getSource().getServer();
      if (server.getPlayerList().getPlayer(uuid(name)) != null) {
         out(c.getSource(), "login " + name + " already-online");
         return;
      }
      ServerLevel level = server.overworld();
      ServerPlayer player = new ServerPlayer(server, level, new GameProfile(uuid(name), name));
      server.getPlayerList().placeNewPlayer(mockConnection(), player);
      MOCKS.put(name, player);
      out(c.getSource(), "login " + name + " uuid=" + player.getUUID() + " online=" + (server.getPlayerList().getPlayer(uuid(name)) != null));
   }

   /** A connection backed by an in-memory channel, as vanilla does for GameTest mock players. */
   private static Connection mockConnection() {
      Connection connection = new Connection(PacketFlow.SERVERBOUND);
      new EmbeddedChannel(connection);
      return connection;
   }

   private static void logout(CommandContext<CommandSourceStack> c) {
      String name = StringArgumentType.getString(c, "name");
      MinecraftServer server = c.getSource().getServer();
      ServerPlayer player = server.getPlayerList().getPlayer(uuid(name));
      if (player == null) {
         out(c.getSource(), "logout " + name + " not-online");
         return;
      }
      server.getPlayerList().remove(player);
      MOCKS.remove(name);
      out(c.getSource(), "logout " + name + " online=" + (server.getPlayerList().getPlayer(uuid(name)) != null));
   }

   private static void respawn(CommandContext<CommandSourceStack> c) {
      String name = StringArgumentType.getString(c, "name");
      MinecraftServer server = c.getSource().getServer();
      ServerPlayer player = server.getPlayerList().getPlayer(uuid(name));
      player.kill();
      ServerPlayer fresh = server.getPlayerList().respawn(player, false);
      MOCKS.put(name, fresh);
      out(c.getSource(), "respawn " + name + " newEntity=" + (fresh != player) + " online=" + (server.getPlayerList().getPlayer(uuid(name)) == fresh));
   }

   private static void printEmc(CommandContext<CommandSourceStack> c) {
      String name = StringArgumentType.getString(c, "name");
      IKnowledgeProvider p = data(name);
      out(c.getSource(), "emc " + name + " = " + (p == null ? "none" : p.getEmc() + " knowledge=" + p.getKnowledge().size()));
   }

   private static void setEmc(CommandContext<CommandSourceStack> c) {
      String name = StringArgumentType.getString(c, "name");
      new TeamKnowledgeProvider(uuid(name)).setEmc(new BigInteger(StringArgumentType.getString(c, "value")));
      out(c.getSource(), "setemc " + name + " = " + emc(name));
   }

   private static void fullKnowledge(CommandContext<CommandSourceStack> c) {
      String name = StringArgumentType.getString(c, "name");
      IKnowledgeProvider p = new TeamKnowledgeProvider(uuid(name));
      p.setFullKnowledge(BoolArgumentType.getBool(c, "on"));
      out(c.getSource(), "fullknowledge " + name + " = " + p.hasFullKnowledge() + " knowledge=" + p.getKnowledge().size());
   }

   private static void hasTeam(CommandContext<CommandSourceStack> c) {
      String name = StringArgumentType.getString(c, "name");
      out(c.getSource(), "hasteam " + name + " = " + TPTeam.isInTeam(uuid(name)));
   }

   private static void knows(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      String name = StringArgumentType.getString(c, "name");
      ItemStack stack = ItemArgument.getItem(c, "item").createItemStack(1, false);
      IKnowledgeProvider p = data(name);
      out(c.getSource(), "knows " + name + " " + stack.getItem() + " = " + (p != null && p.hasKnowledge(ItemInfo.fromStack(stack))));
   }

   private static void value(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      ItemStack stack = ItemArgument.getItem(c, "item").createItemStack(1, false);
      out(c.getSource(), "value " + stack.getItem() + " = " + IEMCProxy.INSTANCE.getValue(stack) + " sell=" + IEMCProxy.INSTANCE.getSellValue(stack));
   }

   private static void putCell(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
      int slot = IntegerArgumentType.getInteger(c, "slot");
      String name = StringArgumentType.getString(c, "name");
      ItemStack cell = new ItemStack(ProjectCell.EMC_STORAGE_CELL.get());
      ProjectCell.setOwnerUUID(cell, uuid(name));
      ProjectCell.setNbtFilter(cell, BoolArgumentType.getBool(c, "filter"));
      DriveBlockEntity drive = (DriveBlockEntity) c.getSource().getLevel().getBlockEntity(pos);
      drive.getInternalInventory().setItemDirect(slot, cell);
      out(c.getSource(), "putcell " + pos.toShortString() + " slot=" + slot + " owner=" + name + " tag=" + cell.getTag());
   }

   private static void takeCell(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
      int slot = IntegerArgumentType.getInteger(c, "slot");
      DriveBlockEntity drive = (DriveBlockEntity) c.getSource().getLevel().getBlockEntity(pos);
      ItemStack old = drive.getInternalInventory().getStackInSlot(slot);
      drive.getInternalInventory().setItemDirect(slot, ItemStack.EMPTY);
      out(c.getSource(), "takecell " + pos.toShortString() + " slot=" + slot + " removed=" + old.getTag());
   }

   private static void count(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      IGrid grid = grid(c);
      AEItemKey key = key(c);
      KeyCounter fresh = new KeyCounter();
      long t0 = System.nanoTime();
      grid.getStorageService().getInventory().getAvailableStacks(fresh);
      long micros = (System.nanoTime() - t0) / 1000;
      KeyCounter cached = grid.getStorageService().getCachedInventory();
      out(c.getSource(), "count " + keyName(key) + " fresh=" + fresh.get(key) + " cached=" + cached.get(key) + " keys=" + fresh.size() + " scanMicros=" + micros);
   }

   private static void insert(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      IGrid grid = grid(c);
      AEItemKey key = key(c);
      long n = LongArgumentType.getLong(c, "n");
      long sim = grid.getStorageService().getInventory().insert(key, n, Actionable.SIMULATE, IActionSource.empty());
      long done = grid.getStorageService().getInventory().insert(key, n, Actionable.MODULATE, IActionSource.empty());
      out(c.getSource(), "insert " + keyName(key) + " n=" + n + " simulated=" + sim + " inserted=" + done);
   }

   private static void extract(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      IGrid grid = grid(c);
      AEItemKey key = key(c);
      long n = LongArgumentType.getLong(c, "n");
      long sim = grid.getStorageService().getInventory().extract(key, n, Actionable.SIMULATE, IActionSource.empty());
      long done = grid.getStorageService().getInventory().extract(key, n, Actionable.MODULATE, IActionSource.empty());
      out(c.getSource(), "extract " + keyName(key) + " n=" + n + " simulated=" + sim + " extracted=" + done);
   }

   private static void placeFlower(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
      String name = StringArgumentType.getString(c, "name");
      Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("projectexpansion", "final_power_flower"));
      ServerLevel level = c.getSource().getLevel();
      level.setBlockAndUpdate(pos, block.defaultBlockState());
      BlockEntityPowerFlower flower = (BlockEntityPowerFlower) level.getBlockEntity(pos);
      flower.owner = uuid(name);
      flower.ownerName = name;
      flower.markDirty();
      out(c.getSource(), "flower place " + pos.toShortString() + " owner=" + name + " matter=" + flower.getMatter().name());
   }

   private static int delay() {
      return Config.tickDelay.get();
   }

   private static int phase(BlockEntityPowerFlower flower) {
      int d = delay();
      int a = flower.hashCode() % d;
      return a < 0 ? a + d : a;
   }

   private static void flowerInfo(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
      BlockEntityPowerFlower flower = (BlockEntityPowerFlower) c.getSource().getLevel().getBlockEntity(pos);
      out(c.getSource(), "flower info stored=" + flower.emc + " perFire=" + flower.getMatter().getPowerFlowerOutputForTicks(delay())
         + " delay=" + delay() + " owner=" + flower.ownerName);
   }

   /**
    * Logs the owner in shortly before the flower's next firing, then checks that the backlog the
    * flower delivers is added on top of the balance the owner had at login (nothing overwritten).
    */
   private static void flowerLoginCheck(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
      String name = StringArgumentType.getString(c, "name");
      ServerLevel level = c.getSource().getLevel();
      BlockEntityPowerFlower flower = (BlockEntityPowerFlower) level.getBlockEntity(pos);
      int d = delay();
      int phase = phase(flower);
      long[] fireAt = {-1};
      BigInteger[] expected = {null};
      out(c.getSource(), "flower logincheck armed delay=" + d + " phase=" + phase);
      TASKS.add(() -> {
         long g = level.getGameTime();
         if (expected[0] == null) {
            long untilFire = Math.floorMod(phase - g, d);
            if (untilFire != 30) {
               return false;
            }
            BigInteger before = emc(name);
            BigInteger backlog = flower.emc;
            BigInteger perFire = flower.getMatter().getPowerFlowerOutputForTicks(d);
            expected[0] = before.add(backlog).add(perFire);
            fireAt[0] = g + 30;
            ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(uuid(name), name));
            level.getServer().getPlayerList().placeNewPlayer(mockConnection(), player);
            MOCKS.put(name, player);
            LOGGER.info("PCTEST flower logincheck login emcAtLogin=" + before + " backlog=" + backlog + " perFire=" + perFire);
            return false;
         }
         if (g < fireAt[0] + 25) {
            return false;
         }
         BigInteger actual = emc(name);
         boolean ok = actual.equals(expected[0]) && flower.emc.signum() == 0;
         LOGGER.info("PCTEST " + (ok ? "PASS" : "FAIL") + " flower-merge expected=" + expected[0] + " actual=" + actual + " flowerStoredAfter=" + flower.emc);
         return true;
      });
   }

   private void serverTick(TickEvent.ServerTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }
      for (Iterator<BooleanSupplier> it = TASKS.iterator(); it.hasNext(); ) {
         if (it.next().getAsBoolean()) {
            it.remove();
         }
      }
   }
}
