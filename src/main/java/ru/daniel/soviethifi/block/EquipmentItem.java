package ru.daniel.soviethifi.block;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import ru.daniel.soviethifi.SovietHiFi;

public final class EquipmentItem extends BlockItem {
    public EquipmentItem(Block block, Properties properties) { super(block, properties); }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (!(getBlock() instanceof EquipmentBlock placed) || context.getClickedFace() != Direction.UP)
            return super.useOn(context);
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var existing = level.getBlockState(pos);
        var player = context.getPlayer();
        BlockState result;
        if (existing.getBlock() instanceof EquipmentBlock base &&
                (base.kind == EquipmentBlock.Kind.DECK && placed.kind == EquipmentBlock.Kind.AMP ||
                 base.kind == EquipmentBlock.Kind.AMP && placed.kind == EquipmentBlock.Kind.DECK)) {
            result = SovietHiFi.STACK.get().defaultBlockState()
                .setValue(EquipmentBlock.FACING, existing.getValue(EquipmentBlock.FACING))
                .setValue(StackedEquipmentBlock.AMP_ON_TOP, placed.kind == EquipmentBlock.Kind.AMP);
        } else return super.useOn(context);
        if (player == null || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()) ||
                level instanceof ServerLevel server && !server.mayInteract(player, pos) ||
                !level.isUnobstructed(result, pos, CollisionContext.of(player))) return InteractionResult.FAIL;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        var old = level.getBlockEntity(pos) instanceof EquipmentEntity entity ? entity : null;
        // Changing block type must transfer the tape instead of dropping or duplicating it.
        if (old != null) old.moving = true;
        boolean changed;
        try { changed = level.setBlock(pos, result, 3); }
        finally { if (old != null) old.moving = false; }
        if (!changed) return InteractionResult.FAIL;
        if (old != null && level.getBlockEntity(pos) instanceof EquipmentEntity next) old.copyPlaybackTo(next);
        if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1, 1);
        return InteractionResult.SUCCESS;
    }
}
