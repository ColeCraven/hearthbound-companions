package io.github.tekless.hearthbound.blueprint;

import io.github.tekless.hearthbound.core.blueprint.Layout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Something a player built, captured block by block so companions can put it up again somewhere else. Kept in
 * the game's own structure file format (the one structure blocks use), with the way the player faced when they
 * saved it, so a file saved by a structure block works too.
 *
 * <p>Only blocks a companion could place from an item are kept: no water or lava, no grass or snow layers, nothing
 * without an item (fire, portals). A door, a bed or a tall flower is kept once, by its lower half or foot, and the
 * other half goes in with it. What was inside chests and furnaces is not copied, only the blocks.
 */
public final class SavedBuild {
	/** Extra tag in the file: which way the player faced when they saved it (the game ignores it). */
	private static final String FRONT_TAG = "hearthbound_front";

	/** One block of the build: where it sits from the corner, and what it is. */
	public record Piece(BlockPos offset, BlockState state) {
	}

	private final String name;
	private final Vec3i size;
	private final Direction front;
	private final String author;
	private final List<Piece> pieces;
	/** The game version the blocks were saved in (0 if unknown), written back so structure blocks read the file right. */
	private int dataVersion;

	SavedBuild(String name, Vec3i size, Direction front, String author, List<Piece> pieces) {
		this.name = name;
		this.size = size;
		this.front = front;
		this.author = author;
		this.pieces = List.copyOf(pieces);
	}

	public String name() {
		return name;
	}

	public Vec3i size() {
		return size;
	}

	public Direction front() {
		return front;
	}

	public String author() {
		return author;
	}

	public List<Piece> pieces() {
		return pieces;
	}

	/**
	 * Captures the blocks between two corners (both included).
	 *
	 * @param front the way the player faces as they save it: the way they'll face it when it's built again
	 */
	public static SavedBuild capture(ServerLevel level, BlockPos a, BlockPos b, Direction front, String name, String author) {
		BlockPos low = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
		Vec3i size = new Vec3i(Math.abs(a.getX() - b.getX()) + 1, Math.abs(a.getY() - b.getY()) + 1, Math.abs(a.getZ() - b.getZ()) + 1);
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, low, size, false, List.of(Blocks.STRUCTURE_VOID));
		template.setAuthor(author);
		CompoundTag tag = template.save(new CompoundTag());
		tag.putString(FRONT_TAG, front.getName());
		return fromTag(name, tag, level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), Direction.NORTH);
	}

	/** The file contents, in the structure block format, plus the facing. */
	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		ListTag sizeTag = new ListTag();
		sizeTag.add(net.minecraft.nbt.IntTag.valueOf(size.getX()));
		sizeTag.add(net.minecraft.nbt.IntTag.valueOf(size.getY()));
		sizeTag.add(net.minecraft.nbt.IntTag.valueOf(size.getZ()));
		tag.put(StructureTemplate.SIZE_TAG, sizeTag);
		List<BlockState> palette = new ArrayList<>();
		Map<BlockState, Integer> index = new LinkedHashMap<>();
		ListTag blocks = new ListTag();
		for (Piece piece : pieces) {
			Integer at = index.get(piece.state());
			if (at == null) {
				at = palette.size();
				palette.add(piece.state());
				index.put(piece.state(), at);
			}
			CompoundTag block = new CompoundTag();
			ListTag pos = new ListTag();
			pos.add(net.minecraft.nbt.IntTag.valueOf(piece.offset().getX()));
			pos.add(net.minecraft.nbt.IntTag.valueOf(piece.offset().getY()));
			pos.add(net.minecraft.nbt.IntTag.valueOf(piece.offset().getZ()));
			block.put(StructureTemplate.BLOCK_TAG_POS, pos);
			block.putInt(StructureTemplate.BLOCK_TAG_STATE, at);
			blocks.add(block);
		}
		ListTag paletteTag = new ListTag();
		for (BlockState state : palette) {
			paletteTag.add(NbtUtils.writeBlockState(state));
		}
		tag.put(StructureTemplate.PALETTE_TAG, paletteTag);
		tag.put(StructureTemplate.BLOCKS_TAG, blocks);
		tag.put(StructureTemplate.ENTITIES_TAG, new ListTag());
		tag.putString("author", author);
		tag.putString(FRONT_TAG, front.getName());
		if (dataVersion > 0) {
			tag.putInt("DataVersion", dataVersion);
		}
		return tag;
	}

	/**
	 * Reads a structure file (one of ours, or one a structure block saved).
	 *
	 * @param defaultFront the facing to assume when the file doesn't say (structure block files never do)
	 */
	public static SavedBuild fromTag(String name, CompoundTag tag, HolderGetter<Block> blockLookup, Direction defaultFront) {
		ListTag sizeTag = tag.getListOrEmpty(StructureTemplate.SIZE_TAG);
		Vec3i size = new Vec3i(sizeTag.getIntOr(0, 0), sizeTag.getIntOr(1, 0), sizeTag.getIntOr(2, 0));
		ListTag paletteTag = tag.getListOrEmpty(StructureTemplate.PALETTE_TAG);
		if (paletteTag.isEmpty()) {
			paletteTag = tag.getListOrEmpty(StructureTemplate.PALETTE_LIST_TAG).getListOrEmpty(0);
		}
		List<BlockState> palette = new ArrayList<>();
		for (int i = 0; i < paletteTag.size(); i++) {
			palette.add(NbtUtils.readBlockState(blockLookup, paletteTag.getCompoundOrEmpty(i)));
		}
		List<Piece> pieces = new ArrayList<>();
		ListTag blocks = tag.getListOrEmpty(StructureTemplate.BLOCKS_TAG);
		for (int i = 0; i < blocks.size() && pieces.size() < Layout.MAX_BLOCKS; i++) {
			CompoundTag block = blocks.getCompoundOrEmpty(i);
			int state = block.getIntOr(StructureTemplate.BLOCK_TAG_STATE, -1);
			ListTag pos = block.getListOrEmpty(StructureTemplate.BLOCK_TAG_POS);
			if (state < 0 || state >= palette.size() || pos.size() != 3) {
				continue;
			}
			BlockState blockState = palette.get(state);
			if (keep(blockState)) {
				pieces.add(new Piece(new BlockPos(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0)), blockState));
			}
		}
		Direction front = Direction.byName(tag.getStringOr(FRONT_TAG, ""));
		if (front == null || front.getAxis().isVertical()) {
			front = defaultFront;
		}
		SavedBuild build = new SavedBuild(name, size, front, tag.getStringOr("author", ""), pieces);
		build.dataVersion = tag.getIntOr("DataVersion", 0);
		return build;
	}

	/** Whether a companion would place this block (see the class notes). */
	static boolean keep(BlockState state) {
		if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.canBeReplaced() || itemFor(state) == Items.AIR) {
			return false;
		}
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
			return false;
		}
		if (state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
			return false;
		}
		String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return !(path.equals("light") || path.equals("barrier") || path.equals("bedrock") || path.equals("moving_piston") || path.equals("piston_head")
				|| path.contains("structure") || path.contains("jigsaw") || path.contains("command_block") || path.contains("spawner")
				|| path.contains("portal") || path.startsWith("test_"));
	}

	/** The item a block is placed from (a torch for a wall torch); air if there isn't one. */
	public static Item itemFor(BlockState state) {
		return state.getBlock().asItem();
	}

	/** How many items placing this block takes: two for a double slab, one otherwise. */
	public static int itemsFor(BlockState state) {
		return state.getBlock() instanceof SlabBlock && state.hasProperty(BlockStateProperties.SLAB_TYPE)
				&& state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE ? 2 : 1;
	}

	/** Everything it takes, item by item. */
	public Map<Item, Integer> materials() {
		Map<Item, Integer> needs = new LinkedHashMap<>();
		for (Piece piece : pieces) {
			needs.merge(itemFor(piece.state()), itemsFor(piece.state()), Integer::sum);
		}
		return needs;
	}

	/** The game's rotation for some clockwise quarter turns. */
	public static Rotation rotation(int quarterTurns) {
		return switch (Math.floorMod(quarterTurns, 4)) {
			case 1 -> Rotation.CLOCKWISE_90;
			case 2 -> Rotation.CLOCKWISE_180;
			case 3 -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	/** The other half of a two-block thing (a door's top, a bed's head), as placed beside {@code at}, or null. */
	public static Piece otherHalf(BlockPos at, BlockState placed) {
		if (placed.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			return new Piece(at.above(), placed.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
		}
		if (placed.getBlock() instanceof BedBlock && placed.hasProperty(BlockStateProperties.BED_PART)) {
			Direction facing = placed.getValue(BedBlock.FACING);
			return new Piece(at.relative(facing), placed.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
		}
		return null;
	}
}
