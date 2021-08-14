package de.srendi.advancedperipherals.common.addons.computercraft.peripheral;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.pocket.IPocketAccess;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.TurtleSide;
import de.srendi.advancedperipherals.common.addons.computercraft.operations.FuelConsumingPeripheral;
import de.srendi.advancedperipherals.common.addons.computercraft.operations.IPeripheralOperation;
import de.srendi.advancedperipherals.common.addons.computercraft.operations.SphereOperationContext;
import de.srendi.advancedperipherals.common.blocks.base.PeripheralTileEntity;
import de.srendi.advancedperipherals.common.configuration.AdvancedPeripheralsConfig;
import de.srendi.advancedperipherals.common.util.LuaConverter;
import de.srendi.advancedperipherals.common.util.ScanUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.Tags;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

import static de.srendi.advancedperipherals.common.addons.computercraft.operations.SphereOperation.SCAN_BLOCKS;

public class GeoScannerPeripheral extends FuelConsumingPeripheral {
	/*
	Highly inspired by https://github.com/SquidDev-CC/plethora/ BlockScanner
	*/

    public GeoScannerPeripheral(String type, PeripheralTileEntity<?> tileEntity) {
        super(type, tileEntity);
    }

    public GeoScannerPeripheral(String type, ITurtleAccess turtle, TurtleSide side) {
        super(type, turtle, side);
    }

    public GeoScannerPeripheral(String type, IPocketAccess pocket) {
        super(type, pocket);
    }

    private static List<Map<String, ?>> scan(Level level, BlockPos center, int radius) {
        List<Map<String, ?>> result = new ArrayList<>();
        ScanUtils.relativeTraverseBlocks(level, center, radius, (state, pos) -> {
            HashMap<String, Object> data = new HashMap<>(6);
            data.put("x", pos.getX());
            data.put("y", pos.getY());
            data.put("z", pos.getZ());

            Block block = state.getBlock();
            ResourceLocation name = block.getRegistryName();
            data.put("name", name == null ? "unknown" : name.toString());
            data.put("tags", LuaConverter.tagsToList(block.getTags()));

            result.add(data);
        });

        return result;
    }

    private static int estimateCost(int radius) {
        if (radius > SCAN_BLOCKS.getMaxCostRadius())
            return -1;

        return SCAN_BLOCKS.getCost(SphereOperationContext.of(radius));
    }

    @Override
    protected int getMaxFuelConsumptionRate() {
        return 1;
    }

    @Override
    protected int _getFuelConsumptionRate() {
        return 1;
    }

    @Override
    protected void _setFuelConsumptionRate(int rate) {
    }

    @Override
    public List<IPeripheralOperation<?>> possibleOperations() {
        return Collections.singletonList(SCAN_BLOCKS);
    }

    @Override
    public boolean isEnabled() {
        return AdvancedPeripheralsConfig.enableGeoScanner;
    }

    @LuaFunction
    public final MethodResult cost(int radius) {
        int estimatedCost = estimateCost(radius);
        if (estimatedCost < 0) {
            return MethodResult.of(null, "Radius is exceed max value");
        }
        return MethodResult.of(estimatedCost);
    }

    @LuaFunction(mainThread = true)
    public final MethodResult chunkAnalyze() {
        Optional<MethodResult> checkResult = cooldownCheck(SCAN_BLOCKS);
        if (checkResult.isPresent()) return checkResult.get();
        Level world = getLevel();
        LevelChunk chunk = world.getChunkAt(getPos());
        ChunkPos chunkPos = chunk.getPos();
        HashMap<String, Integer> data = new HashMap<>();
        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                for (int y = 0; y < 256; y++) {
                    BlockState block = chunk.getBlockState(new BlockPos(x, y, z));
                    ResourceLocation name = block.getBlock().getRegistryName();
                    if (name != null) {
                        if (block.is(Tags.Blocks.ORES)) {
                            data.put(name.toString(), data.getOrDefault(name.toString(), 0) + 1);
                        }
                    }
                }
            }
        }
        trackOperation(SCAN_BLOCKS, SCAN_BLOCKS.free());
        return MethodResult.of(data);
    }

    @LuaFunction(mainThread = true)
    public final MethodResult scan(@Nonnull IArguments arguments) throws LuaException {
        int radius = arguments.getInt(0);
        Optional<MethodResult> checkResult = cooldownCheck(SCAN_BLOCKS);
        if (checkResult.isPresent()) return checkResult.get();
        BlockPos pos = getPos();
        Level world = getLevel();
        if (radius > SCAN_BLOCKS.getMaxCostRadius()) {
            return MethodResult.of(null, "Radius is exceed max value");
        }
        if (radius > SCAN_BLOCKS.getMaxFreeRadius()) {
            if (owner.getFuelMaxCount() == 0) {
                return MethodResult.of(null, "Radius is exceed max value");
            }
            int cost = estimateCost(radius);
            if (!consumeFuel(cost, false)) {
                return MethodResult.of(null, String.format("Not enough fuel, %d needed", cost));
            }
        }
        List<Map<String, ?>> scanResult = scan(world, pos, radius);
        trackOperation(SCAN_BLOCKS, SphereOperationContext.of(radius));
        return MethodResult.of(scanResult);
    }
}
