package ru.daniel.soviethifi.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import ru.daniel.soviethifi.SovietHiFi;
import ru.daniel.soviethifi.network.Network;

public class EquipmentBlock extends BaseEntityBlock {
    public enum Kind { SPEAKER, DECK, AMP, RACK, STACK }
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final IntegerProperty TAPES = IntegerProperty.create("tapes", 0, 12);
    public final Kind kind;
    public EquipmentBlock(Kind kind, Properties properties) {
        super(properties); this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false).setValue(TAPES, 0));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(p -> new EquipmentBlock(kind, p)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, POWERED, TAPES); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) { return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()); }
    @Override protected BlockState rotate(BlockState state, Rotation r) { return state.setValue(FACING, r.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror m) { return rotate(state, m.getRotation(state.getValue(FACING))); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new EquipmentEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, SovietHiFi.EQUIPMENT.get(), EquipmentEntity::tick);
    }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        boolean turn = s.getValue(FACING).getAxis() == Direction.Axis.X;
        return switch (kind) {
            case SPEAKER -> turn ? box(2, 0, 3, 14, 16, 13) : box(3, 0, 2, 13, 16, 14);
            case DECK -> box(0, 0, 0, 16, 8, 16);
            case AMP -> box(0, 0, 0, 16, 5, 16);
            case STACK -> box(0, 0, 0, 16, 13, 16);
            case RACK -> box(0, 0, 0, 16, 16, 16);
        };
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(SovietHiFi.CASSETTE.get()) && (kind == Kind.DECK || kind == Kind.RACK || kind == Kind.STACK)) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof EquipmentEntity e) e.insert(player, stack);
            return InteractionResult.SUCCESS;
        }
        if (stack.getItem() instanceof BlockItem) return InteractionResult.PASS;
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof EquipmentEntity e) {
            if (player.isShiftKeyDown() && (kind == Kind.DECK || kind == Kind.RACK || kind == Kind.STACK)) e.eject(player);
            else if (kind == Kind.SPEAKER) sp.sendOverlayMessage(net.minecraft.network.chat.Component.literal("S-90 • поставьте две колонки в пределах 4 блоков от Амфитона"));
            else Network.send(sp, new Network.OpenUi(kind == Kind.RACK ? 2 : 1, pos, false));
        }
        return InteractionResult.SUCCESS;
    }
}
